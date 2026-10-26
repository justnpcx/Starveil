package com.justnpcx.starveil.core.kernel.ruleset

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.util.zip.InflaterInputStream

/**
 * A reader for sing-box rule-set files.
 *
 * The format is a header, a zlib stream, and then a count and a sequence of
 * rules:
 *
 *     "SRS" | version:u8 | zlib( ruleCount:uvarint | rule* )
 *     rule  = type:u8 (0 = default, 1 = logical) + items
 *     item  = type:u8 + payload, terminated by 0xFF + invert:u8
 *
 * Only what a rule *matches on* is kept. The kernel compiles the same file into
 * a matcher; this reads it into lists, because the one thing this reader exists
 * for is answering "would this domain hit this set" without a kernel.
 *
 * Domain sets are stored as a succinct trie over reversed names, so a plain
 * domain list has to be recovered by walking the trie — that is [readDomainSet]
 * below, and it is the only part of the format that is not a straight read.
 */
object Srs {

    private val MAGIC = byteArrayOf(0x53, 0x52, 0x53) // "SRS"

    /** The newest version this reader was written against. */
    private const val SUPPORTED_VERSION = 3

    fun read(bytes: ByteArray): SrsRuleSet {
        val input = SrsInput(ByteArrayInputStream(bytes))
        val magic = input.readFully(3)
        if (!magic.contentEquals(MAGIC)) {
            throw SrsFormatException("not a rule-set file: header is not SRS")
        }
        val version = input.readByte()
        if (version > SUPPORTED_VERSION) {
            throw SrsFormatException("rule-set version $version is newer than $SUPPORTED_VERSION")
        }
        val inflated = SrsInput(InflaterInputStream(input.stream()))
        val ruleCount = inflated.readUvarint()
        val rules = ArrayList<SrsRule>(ruleCount.toInt())
        repeat(ruleCount.toInt()) { index ->
            try {
                rules += readRule(inflated)
            } catch (e: SrsFormatException) {
                throw SrsFormatException("rule[$index]: ${e.message}")
            }
        }
        return SrsRuleSet(version, rules)
    }

    private fun readRule(input: SrsInput): SrsRule {
        return when (val type = input.readByte()) {
            0 -> readDefaultRule(input)
            1 -> throw SrsFormatException(
                "logical rule (type 1) is not supported by this reader",
            )
            else -> throw SrsFormatException("unknown rule type $type")
        }
    }

    private fun readDefaultRule(input: SrsInput): SrsRule {
        var exact = emptyList<String>()
        var suffixes = emptyList<String>()
        var keywords = emptyList<String>()
        var regexes = emptyList<String>()
        var networks = emptyList<String>()
        var ports = emptyList<Int>()
        var ipRanges = emptyList<IpRange>()
        var invert = false
        while (true) {
            val itemType = input.readByte()
            when (itemType) {
                0 -> input.readU16Slice() // query type
                1 -> networks = input.readStringList() // network
                2 -> readDomainSet(input).let { (d, s) ->
                    exact = d
                    suffixes = s
                }
                3 -> keywords = input.readStringList()
                4 -> regexes = input.readStringList()
                5 -> input.readIpSet() // source ip_cidr, kept only to stay in sync
                6 -> ipRanges = input.readIpSet()
                7 -> input.readU16Slice() // source port
                8 -> input.readStringList() // source port range
                9 -> ports = input.readU16Slice()
                10 -> input.readStringList() // port range
                11 -> input.readStringList() // process name
                12 -> input.readStringList() // process path
                13 -> input.readStringList() // package name
                14 -> input.readStringList() // wifi ssid
                15 -> input.readStringList() // wifi bssid
                16 -> throw SrsFormatException("adguard domain rules are not supported")
                17 -> input.readStringList() // process path regex
                18 -> input.readStringList() // network type
                19 -> input.readByte() // network is expensive
                20 -> input.readByte() // network is constrained
                21 -> throw SrsFormatException(
                    "interface address matchers are not supported by this reader",
                )
                22 -> throw SrsFormatException(
                    "default interface address matchers are not supported by this reader",
                )
                23 -> input.readStringList() // package name regex
                0xFF -> {
                    invert = input.readByte() != 0
                    return SrsRule(
                        exactDomains = exact,
                        domainSuffixes = suffixes,
                        domainKeywords = keywords,
                        domainRegexes = regexes,
                        networks = networks,
                        ports = ports,
                        ipRanges = ipRanges,
                        invert = invert,
                    )
                }
                else -> throw SrsFormatException("unknown rule item type $itemType")
            }
        }
    }

    /**
     * An IP set is a version byte, a **big-endian uint64** count (not a
     * uvarint — the format is inconsistent here, and sing-box's own comment
     * says so), then that many from/to pairs. Each address is a uvarint length
     * of 4 or 16 followed by the bytes.
     */
    private fun SrsInput.readIpSet(): List<IpRange> {
        val version = readByte()
        if (version != 1) throw SrsFormatException("unknown IP set version $version")
        val length = readU64BigEndian()
        return List(length.toInt()) {
            val from = readIpSetAddress()
            val to = readIpSetAddress()
            IpRange(from.first, from.second, to.first, to.second, from.third)
        }
    }

    private fun SrsInput.readIpSetAddress(): Triple<Long, Long, Boolean> {
        val length = readUvarint().toInt()
        if (length != 4 && length != 16) {
            throw SrsFormatException("IP address of $length bytes")
        }
        val bytes = readFully(length)
        // A 4-byte address is the low half of the pair with a zero high half,
        // so a v4 range compares as one Long. Shifting it into the high half
        // would make every v4 lookup miss.
        val high: Long
        val low: Long
        if (length == 4) {
            high = 0L
            low = ((bytes[0].toLong() and 0xFF) shl 24) or
                ((bytes[1].toLong() and 0xFF) shl 16) or
                ((bytes[2].toLong() and 0xFF) shl 8) or
                (bytes[3].toLong() and 0xFF)
        } else {
            high = bytes.copyOfRange(0, 8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
            low = bytes.copyOfRange(8, 16).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        }
        return Triple(high, low, length == 16)
    }

    /**
     * A domain set: a succinct trie over *reversed* names.
     *
     * Three kinds of key come out of it, told apart by the first byte after the
     * reversal — that is the whole convention:
     *
     * - `\n` (root) marks "this name and everything under it";
     * - `\r` (prefix) marks "everything under it, not the name itself", and is
     *   what a legacy `.example.com` suffix becomes;
     * - anything else is an exact name.
     *
     * The markers are stripped into [SrsRule.domainSuffixes], where a leading
     * dot keeps the distinction: `.example.com` matches only subdomains,
     * `example.com` matches the name and its subdomains.
     */
    private fun readDomainSet(input: SrsInput): Pair<List<String>, List<String>> {
        input.readByte() // reserved
        val leaves = input.readU64Slice()
        val labelBitmap = input.readU64Slice()
        val labels = input.readByteSlice()

        val ones = labelBitmap.sumOf { java.lang.Long.bitCount(it) }
        var lastOne = -1
        for ((wordIndex, word) in labelBitmap.withIndex()) {
            if (word != 0L) lastOne = wordIndex * 64 + (63 - java.lang.Long.numberOfLeadingZeros(word))
        }
        val zeros = lastOne + 1 - ones
        if (ones != zeros + 1 || labels.size != zeros) {
            throw SrsFormatException(
                "malformed domain set: $ones ones, $zeros zeros, ${labels.size} labels",
            )
        }

        val trie = Trie(leaves, labelBitmap, labels)
        val exact = ArrayList<String>()
        val suffixes = ArrayList<String>()
        for (key in trie.keys()) {
            val name = reverseDomain(key)
            when (name.firstOrNull()) {
                PREFIX_LABEL -> suffixes += name.substring(1)
                ROOT_LABEL -> suffixes += name.substring(1)
                else -> exact += name
            }
        }
        return exact to suffixes
    }

    /** The two marker bytes sing-box puts in front of a reversed name. */
    private const val PREFIX_LABEL = '\r'
    private const val ROOT_LABEL = '\n'

    /**
     * Names are stored reversed, so a walk yields suffix-first strings. The
     * reversal is by code point in sing-box; domain names are ASCII (IDNs are
     * punycode), so a byte reversal is the same thing for every name this
     * reader will ever see, and it is what this does.
     */
    private fun reverseDomain(value: String): String {
        val bytes = value.toByteArray(Charsets.UTF_8)
        for (i in 0 until bytes.size / 2) {
            val swap = bytes[i]
            bytes[i] = bytes[bytes.size - 1 - i]
            bytes[bytes.size - 1 - i] = swap
        }
        return String(bytes, Charsets.UTF_8)
    }
}

/** A parsed rule-set file. */
data class SrsRuleSet(
    val version: Int,
    val rules: List<SrsRule>,
) {
    val exactDomains: List<String> get() = rules.flatMap { it.exactDomains }
    val domainSuffixes: List<String> get() = rules.flatMap { it.domainSuffixes }
    val domainKeywords: List<String> get() = rules.flatMap { it.domainKeywords }
}

/** One rule: the matchers it carries, and whether it is inverted. */
data class SrsRule(
    val exactDomains: List<String> = emptyList(),
    val domainSuffixes: List<String> = emptyList(),
    val domainKeywords: List<String> = emptyList(),
    val domainRegexes: List<String> = emptyList(),
    val networks: List<String> = emptyList(),
    val ports: List<Int> = emptyList(),
    val ipRanges: List<IpRange> = emptyList(),
    val invert: Boolean = false,
)

/**
 * One from/to pair from an IP set.
 *
 * IPv4 addresses land in [low] with [high] zero, so a v4 lookup is one
 * comparison against a Long and needs no boxing. [isV6] keeps the two families
 * from being compared to each other.
 */
data class IpRange(
    val fromHigh: Long,
    val fromLow: Long,
    val toHigh: Long,
    val toLow: Long,
    val isV6: Boolean,
) {
    fun contains(high: Long, low: Long, isV6: Boolean): Boolean {
        if (this.isV6 != isV6) return false
        if (high < fromHigh || high > toHigh) return false
        if (high == fromHigh && low < fromLow) return false
        if (high == toHigh && low > toLow) return false
        return true
    }
}

class SrsFormatException(message: String) : IOException(message)

/**
 * The succinct trie: a bit vector of labels per node, a bit vector marking the
 * last child of each node, and a bit vector marking which nodes are keys.
 *
 * A node's children are the labels between its own position and the next set
 * bit in [labelBitmap]; a node is a key when its bit in [leaves] is set.
 */
private class Trie(
    private val leaves: LongArray,
    private val labelBitmap: LongArray,
    private val labels: ByteArray,
) {

    /** Cumulative one counts, so rank is a table read. */
    private val ranks = IntArray(labelBitmap.size + 1).also { table ->
        var total = 0
        for (i in labelBitmap.indices) {
            table[i] = total
            total += java.lang.Long.bitCount(labelBitmap[i])
        }
        table[labelBitmap.size] = total
    }

    fun keys(): List<String> {
        val result = ArrayList<String>()
        val key = ByteArrayBuilder()
        // Two ints per frame: the node id, and the position in labelBitmap.
        // The root is frame 0 and stays on the stack; depth counts frames.
        var stack = IntArray(2 * 64)
        stack[0] = 0
        stack[1] = 0
        var depth = 1

        if (bit(leaves, 0)) result += ""

        while (depth > 0) {
            val top = (depth - 1) * 2
            val nodeId = stack[top]
            val bmIdx = stack[top + 1]
            if (bit(labelBitmap, bmIdx)) {
                // This node has no more children: step back and advance the
                // parent past the child we just finished.
                depth--
                if (depth > 0) {
                    key.removeLast()
                    stack[(depth - 1) * 2 + 1] = stack[(depth - 1) * 2 + 1] + 1
                }
                continue
            }
            key.add(labels[bmIdx - nodeId])
            val nextNodeId = countZeros(bmIdx + 1)
            val nextBmIdx = select(nextNodeId - 1) + 1
            if (bit(leaves, nextNodeId)) result += key.toString()
            if ((depth + 1) * 2 > stack.size) stack = stack.copyOf(stack.size * 2)
            stack[depth * 2] = nextNodeId
            stack[depth * 2 + 1] = nextBmIdx
            depth++
        }
        return result
    }

    private fun bit(words: LongArray, index: Int): Boolean {
        val word = index ushr 6
        if (word >= words.size) return false
        return (words[word] ushr (index and 63)) and 1L == 1L
    }

    /** How many zeros precede [index] in the label bitmap. */
    private fun countZeros(index: Int): Int {
        val word = index ushr 6
        val before = if (word >= labelBitmap.size) {
            ranks[labelBitmap.size]
        } else {
            ranks[word] + java.lang.Long.bitCount(
                labelBitmap[word] and ((1L shl (index and 63)) - 1),
            )
        }
        return index - before
    }

    /** The position of the [index]-th one bit, counted from zero. */
    private fun select(index: Int): Int {
        if (index < 0) return 0
        var low = 0
        var high = labelBitmap.size - 1
        while (low < high) {
            val mid = (low + high + 1) ushr 1
            if (ranks[mid] <= index) low = mid else high = mid - 1
        }
        var remaining = index - ranks[low]
        var word = labelBitmap[low]
        while (true) {
            val next = java.lang.Long.numberOfTrailingZeros(word)
            if (remaining == 0) return low * 64 + next
            remaining--
            word = word and (word - 1)
        }
    }
}

private class ByteArrayBuilder {
    private var bytes = ByteArray(64)
    private var size = 0

    fun add(value: Byte) {
        if (size == bytes.size) bytes = bytes.copyOf(size * 2)
        bytes[size++] = value
    }

    fun removeLast() {
        if (size > 0) size--
    }

    override fun toString(): String = String(bytes, 0, size, Charsets.UTF_8)
}

/**
 * The byte reader the format is written against: LEB128 counts, big-endian
 * fixed-width slices, and length-prefixed strings.
 */
private class SrsInput(private val input: InputStream) {

    fun stream(): InputStream = input

    fun readByte(): Int {
        val value = input.read()
        if (value < 0) throw EOFException("unexpected end of rule-set file")
        return value
    }

    fun readFully(count: Int): ByteArray {
        val buffer = ByteArray(count)
        var read = 0
        while (read < count) {
            val n = input.read(buffer, read, count - read)
            if (n < 0) throw EOFException("unexpected end of rule-set file")
            read += n
        }
        return buffer
    }

    fun readUvarint(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            val byte = readByte()
            result = result or ((byte.toLong() and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
            if (shift > 63) throw SrsFormatException("uvarint is too long")
        }
    }

    /** One big-endian uint64. The IP-set count is written this way, not as a uvarint. */
    fun readU64BigEndian(): Long {
        val bytes = readFully(8)
        var value = 0L
        for (b in bytes) value = (value shl 8) or (b.toLong() and 0xFF)
        return value
    }

    fun readU64Slice(): LongArray {
        val count = readUvarint().toInt()
        val bytes = readFully(count * 8)
        val result = LongArray(count)
        for (i in 0 until count) {
            var value = 0L
            for (b in 0 until 8) {
                value = (value shl 8) or (bytes[i * 8 + b].toLong() and 0xFF)
            }
            result[i] = value
        }
        return result
    }

    fun readU16Slice(): List<Int> {
        val count = readUvarint().toInt()
        val bytes = readFully(count * 2)
        return List(count) { i ->
            ((bytes[i * 2].toInt() and 0xFF) shl 8) or (bytes[i * 2 + 1].toInt() and 0xFF)
        }
    }

    fun readByteSlice(): ByteArray {
        val count = readUvarint().toInt()
        return readFully(count)
    }

    fun readString(): String = String(readByteSlice(), Charsets.UTF_8)

    fun readStringList(): List<String> {
        val count = readUvarint().toInt()
        return List(count) { readString() }
    }
}

