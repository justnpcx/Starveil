package com.justnpcx.starveil.feature.profiles

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.multi.GenericMultipleBarcodeReader
import com.justnpcx.starveil.core.ui.components.StarveilButton
import com.justnpcx.starveil.core.ui.components.StarveilIconAction
import com.justnpcx.starveil.core.ui.icons.StarveilIcons
import com.justnpcx.starveil.core.ui.theme.StarveilColors
import com.justnpcx.starveil.core.ui.theme.StarveilSpacing
import com.justnpcx.starveil.core.ui.theme.StarveilType
import java.util.concurrent.Executors

/**
 * The scanner: full screen, and it does not close on the first code.
 *
 * A proxy user almost never has one node on one code — a subscription export
 * page is a grid of them, and a provider's onboarding sheet often carries
 * three. Closing after the first scan means re-opening the camera for each,
 * so the page stays open, counts what it has, and closes when the user says so.
 *
 * Decoding is ZXing over a CameraX frame, on a background thread, with the
 * result handed back to the main thread. Nothing is uploaded: the same decoder
 * also reads a picture from the gallery.
 */
@Composable
fun QrScannerScreen(
    onCancel: () -> Unit,
    onDone: (List<String>) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var scanned by remember { mutableStateOf<List<String>>(emptyList()) }
    var torchOn by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }
    var pendingSubscription by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val askPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted) askPermission.launch(Manifest.permission.CAMERA)
    }

    // One acceptance path for both the camera and the gallery, so a code read
    // from a screenshot and a code read from the lens behave identically.
    val accept: (List<String>) -> Unit = { found ->
        val fresh = found.filter { it.isNotBlank() && it !in scanned }
        if (fresh.isNotEmpty()) {
            val subscription = fresh.firstOrNull { isSubscriptionUrl(it) }
            if (subscription != null && pendingSubscription == null && fresh.size == 1) {
                pendingSubscription = subscription
            } else {
                scanned = scanned + fresh
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                flash = true
            }
        }
    }

    // The border flashes for 200ms on a hit: the confirmation is where the eye
    // already is, not in a toast at the bottom of the screen.
    LaunchedEffect(flash) {
        if (flash) {
            kotlinx.coroutines.delay(200)
            flash = false
        }
    }
    val frameAlpha by animateFloatAsState(
        targetValue = if (flash) 1f else 0.85f,
        animationSpec = tween(durationMillis = 200),
        label = "scan-frame",
    )

    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(granted, lifecycleOwner) {
        if (!granted) return@DisposableEffect onDispose { }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        var provider: ProcessCameraProvider? = null
        providerFuture.addListener({
            provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { image ->
                val found = runCatching { decodeFrame(image) }.getOrDefault(emptyList())
                image.close()
                if (found.isNotEmpty()) mainHandler.post { accept(found) }
            }
            runCatching {
                camera = provider?.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            provider?.unbindAll()
            executor.shutdown()
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val bitmap = runCatching {
            context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
        }.getOrNull()
        if (bitmap != null) accept(decodeBitmap(bitmap))
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // The viewfinder: a 24dp rounded frame, the accent colour, and nothing
        // else over the preview.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(0.72f)
                .aspectRatio(1f)
                .border(
                    width = 2.dp,
                    color = StarveilColors.accent.copy(alpha = frameAlpha),
                    shape = RoundedCornerShape(24.dp),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(StarveilSpacing.lg),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarveilIconAction(
                    icon = StarveilIcons.Close,
                    contentDescription = stringResource(R.string.scan_close),
                    onClick = onCancel,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (scanned.isNotEmpty()) {
                    CounterBadge(count = scanned.size)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(StarveilSpacing.md)) {
                if (!granted) {
                    Text(
                        text = stringResource(R.string.scan_permission),
                        style = StarveilType.body,
                        color = Color.White,
                    )
                }
                pendingSubscription?.let { url ->
                    // A subscription link is not a node: say so, and let the
                    // user turn it into the import it actually is.
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StarveilColors.card)
                            .padding(StarveilSpacing.md),
                        verticalArrangement = Arrangement.spacedBy(StarveilSpacing.sm),
                    ) {
                        Text(
                            text = stringResource(R.string.scan_subscription_confirm),
                            style = StarveilType.body,
                            color = StarveilColors.ink,
                        )
                        Text(
                            text = url,
                            style = StarveilType.label,
                            color = StarveilColors.ink2,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.sm)) {
                            StarveilButton(
                                text = stringResource(R.string.scan_subscription_accept),
                                onClick = {
                                    scanned = scanned + url
                                    pendingSubscription = null
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    flash = true
                                },
                            )
                            StarveilButton(
                                text = stringResource(R.string.scan_subscription_skip),
                                secondary = true,
                                onClick = { pendingSubscription = null },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(StarveilSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction(
                        icon = StarveilIcons.Sun,
                        description = stringResource(
                            if (torchOn) R.string.scan_torch_off else R.string.scan_torch_on,
                        ),
                        selected = torchOn,
                        onClick = {
                            torchOn = !torchOn
                            camera?.cameraControl?.enableTorch(torchOn)
                        },
                    )
                    RoundAction(
                        icon = StarveilIcons.Search,
                        description = stringResource(R.string.scan_gallery),
                        onClick = { pickImage.launch("image/*") },
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    StarveilButton(
                        text = if (scanned.isEmpty()) {
                            stringResource(R.string.scan_cancel)
                        } else {
                            stringResource(R.string.scan_done, scanned.size)
                        },
                        onClick = {
                            if (scanned.isEmpty()) onCancel() else onDone(scanned)
                        },
                    )
                }
            }
        }
    }
}

/** "已扫 N 个", over the preview. */
@Composable
private fun CounterBadge(count: Int) {
    Text(
        text = stringResource(R.string.scan_count, count),
        style = StarveilType.label,
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = StarveilSpacing.md, vertical = StarveilSpacing.xs),
    )
}

/** One of the round controls under the viewfinder. */
@Composable
private fun RoundAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) StarveilColors.accent else Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** http(s) is a subscription, everything else is a node link. */
private fun isSubscriptionUrl(text: String): Boolean =
    text.startsWith("http://", ignoreCase = true) ||
        text.startsWith("https://", ignoreCase = true)

private val READER = MultiFormatReader().apply {
    setHints(
        mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
            DecodeHintType.TRY_HARDER to true,
        ),
    )
}

private val MULTI_READER = GenericMultipleBarcodeReader(READER)

/** Decodes every QR code in a camera frame. Runs on the analysis thread. */
private fun decodeFrame(image: ImageProxy): List<String> {
    val plane = image.planes[0]
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val source = PlanarYUVLuminanceSource(
        bytes,
        image.width,
        image.height,
        0,
        0,
        image.width,
        image.height,
        false,
    )
    return MULTI_READER.decodeMultiple(BinaryBitmap(HybridBinarizer(source)))
        .map { it.text }
        .filter { it.isNotBlank() }
}

/** Decodes every QR code in a picture from the gallery. */
private fun decodeBitmap(bitmap: Bitmap): List<String> {
    val width = bitmap.width
    val height = bitmap.height
    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val source = RGBLuminanceSource(width, height, pixels)
    return MULTI_READER.decodeMultiple(BinaryBitmap(HybridBinarizer(source)))
        .map { it.text }
        .filter { it.isNotBlank() }
}
