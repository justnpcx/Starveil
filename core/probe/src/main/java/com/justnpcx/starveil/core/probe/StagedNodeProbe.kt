package com.justnpcx.starveil.core.probe

import com.justnpcx.starveil.core.data.geo.DohResolver
import com.justnpcx.starveil.core.model.ProbeStage
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.StageNote
import com.justnpcx.starveil.core.model.StageOutcome
import com.justnpcx.starveil.core.model.StageResult
import com.justnpcx.starveil.core.model.StagedProbePort
import com.justnpcx.starveil.core.model.StagedProbeReport
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.inject.Inject
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the same path the kernel would, one layer at a time, for diagnosis.
 *
 * Nothing here writes to the health store: the score exists to choose a node,
 * and opening a diagnostic must not move it.
 *
 * TLS is measured with certificate checks off. The question is "does the
 * server complete a handshake", not "is this certificate trusted": a REALITY
 * node presents someone else's certificate on purpose, and a self-signed node
 * works fine. No application data is sent on that socket — a handshake and a
 * close, which is why this is a probe and not a MITM.
 */
class StagedNodeProbe @Inject constructor(
    private val dns: DohResolver,
) : StagedProbePort {

    override suspend fun probe(profile: Profile, timeoutMs: Int): StagedProbeReport =
        withContext(Dispatchers.IO) {
            val stages = mutableListOf<StageResult>()
            val isUdp = profile.type in UDP_PROTOCOLS

            // ---- DNS ----------------------------------------------------
            val literal = isLiteral(profile.server)
            val address: String?
            val dnsStarted = System.nanoTime()
            if (literal) {
                address = profile.server
                stages += StageResult(ProbeStage.DNS, StageOutcome.SKIPPED, note = StageNote.LITERAL_HOST)
            } else {
                address = try {
                    dns.resolveStrict(profile.server)
                } catch (e: Exception) {
                    null
                }
                val dnsMs = (System.nanoTime() - dnsStarted) / 1_000_000
                stages += if (address == null) {
                    StageResult(ProbeStage.DNS, StageOutcome.FAILED, dnsMs)
                } else {
                    StageResult(ProbeStage.DNS, StageOutcome.OK, dnsMs)
                }
            }
            if (address == null) {
                stages += StageResult(ProbeStage.TCP, StageOutcome.SKIPPED)
                stages += StageResult(ProbeStage.TLS, StageOutcome.SKIPPED, note = StageNote.NO_TLS)
                stages += StageResult(ProbeStage.HTTP, StageOutcome.SKIPPED, note = StageNote.NO_HTTP)
                return@withContext StagedProbeReport(address = null, stages = stages)
            }

            // ---- TCP ----------------------------------------------------
            if (isUdp) {
                stages += StageResult(ProbeStage.TCP, StageOutcome.SKIPPED, note = StageNote.UDP_PROTOCOL)
                stages += StageResult(ProbeStage.TLS, StageOutcome.SKIPPED, note = StageNote.UDP_PROTOCOL)
                stages += StageResult(ProbeStage.HTTP, StageOutcome.SKIPPED, note = StageNote.NO_HTTP)
                return@withContext StagedProbeReport(address = address, stages = stages)
            }

            val tcpStarted = System.nanoTime()
            val tcp = connect(address, profile.port, timeoutMs)
            val tcpMs = (System.nanoTime() - tcpStarted) / 1_000_000
            stages += StageResult(ProbeStage.TCP, tcp, tcpMs)
            if (tcp != StageOutcome.OK) {
                stages += StageResult(ProbeStage.TLS, StageOutcome.SKIPPED, note = StageNote.NO_TLS)
                stages += StageResult(ProbeStage.HTTP, StageOutcome.SKIPPED, note = StageNote.NO_HTTP)
                return@withContext StagedProbeReport(address = address, stages = stages)
            }

            // ---- TLS ----------------------------------------------------
            val tls = tlsOptions(profile)
            when {
                tls == null -> stages += StageResult(ProbeStage.TLS, StageOutcome.SKIPPED, note = StageNote.NO_TLS)
                tls.security == SecurityMode.REALITY ->
                    stages += StageResult(ProbeStage.TLS, StageOutcome.SKIPPED, note = StageNote.REALITY_HANDSHAKE)
                else -> {
                    val started = System.nanoTime()
                    val outcome = handshake(address, profile.port, tls.sni ?: profile.server, timeoutMs)
                    stages += StageResult(ProbeStage.TLS, outcome, (System.nanoTime() - started) / 1_000_000)
                }
            }

            // ---- HTTP ---------------------------------------------------
            // No proxy protocol in this app is an HTTP proxy: a request to the
            // node's port would be answered by the proxy protocol, not by an
            // HTTP server, so measuring one would report a failure that means
            // nothing. The row stays so the four layers are always visible.
            stages += StageResult(ProbeStage.HTTP, StageOutcome.SKIPPED, note = StageNote.NO_HTTP)
            StagedProbeReport(address = address, stages = stages)
        }

    private fun connect(address: String, port: Int, timeoutMs: Int): StageOutcome {
        val socket = Socket()
        return try {
            socket.connect(InetSocketAddress(address, port), timeoutMs)
            StageOutcome.OK
        } catch (e: SocketTimeoutException) {
            StageOutcome.TIMEOUT
        } catch (e: IOException) {
            StageOutcome.FAILED
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun handshake(address: String, port: Int, sni: String, timeoutMs: Int): StageOutcome {
        var socket: SSLSocket? = null
        return try {
            val plain = Socket()
            plain.connect(InetSocketAddress(address, port), timeoutMs)
            plain.soTimeout = timeoutMs
            val ssl = TRUSTING_CONTEXT.socketFactory.createSocket(
                plain,
                if (isLiteral(sni)) address else sni,
                port,
                true,
            ) as SSLSocket
            socket = ssl
            if (!isLiteral(sni)) {
                ssl.sslParameters = ssl.sslParameters.apply {
                    serverNames = listOf(SNIHostName(sni))
                    // The handshake is the measurement; hostname verification
                    // would only say whether the certificate matches, which is
                    // a different question and not one a diagnostic can answer
                    // for a REALITY node.
                    endpointIdentificationAlgorithm = null
                }
            }
            ssl.startHandshake()
            StageOutcome.OK
        } catch (e: SocketTimeoutException) {
            StageOutcome.TIMEOUT
        } catch (e: Exception) {
            StageOutcome.FAILED
        } finally {
            runCatching { socket?.close() }
        }
    }

    private fun tlsOptions(profile: Profile): TlsOptions? = when (val settings = profile.settings) {
        is VlessSettings -> settings.tls
        is VmessSettings -> settings.tls
        is TrojanSettings -> settings.tls
        else -> null
    }

    private fun isLiteral(host: String): Boolean =
        host.contains(':') || (host.count { it == '.' } == 3 && host.all { it.isDigit() || it == '.' })

    private companion object {
        val UDP_PROTOCOLS = setOf(ProfileType.HYSTERIA2, ProfileType.TUIC, ProfileType.WIREGUARD)

        /**
         * A context that completes the handshake without judging the
         * certificate. See the class comment: the socket carries no
         * application data, and a REALITY node deliberately presents a
         * certificate that is not its own.
         */
        val TRUSTING_CONTEXT: SSLContext = SSLContext.getInstance("TLS").apply {
            init(
                null,
                arrayOf<TrustManager>(object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
                    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                }),
                null,
            )
        }
    }
}
