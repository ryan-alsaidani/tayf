package com.tayf.app.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.tayf.app.AppContainer
import com.tayf.app.R
import com.tayf.app.core.ScanMode
import com.tayf.app.ui.components.EmptyState
import com.tayf.app.ui.components.TayfTopBar
import com.tayf.app.ui.components.findActivity
import com.tayf.app.ui.components.toast
import com.tayf.app.ui.nav.Routes
import com.tayf.app.ui.theme.TayfBrown
import com.tayf.app.ui.theme.TayfDanger
import com.tayf.app.ui.theme.TayfGold
import com.tayf.app.ui.theme.TayfGoldLight
import com.tayf.app.ui.theme.TayfIcons
import com.tayf.app.ui.theme.TayfOk
import com.tayf.app.ui.theme.TayfText
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

private suspend fun android.content.Context.awaitCameraProvider(): ProcessCameraProvider = suspendCoroutine { cont ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener({ cont.resume(future.get()) }, ContextCompat.getMainExecutor(this))
}

private fun modeTitle(mode: ScanMode): Int = when (mode) {
    ScanMode.SINGLE -> R.string.mode_single
    ScanMode.BATCH -> R.string.mode_batch
    ScanMode.ID_CARD -> R.string.mode_id_card
    ScanMode.BOOK -> R.string.mode_book
    ScanMode.WHITEBOARD -> R.string.mode_whiteboard
    ScanMode.LONG -> R.string.mode_long
}

@Composable
fun CameraScreen(container: AppContainer, navController: NavHostController, mode: ScanMode, docId: Long?) {
    val context = LocalContext.current
    val vm: CameraViewModel = viewModel(key = "camera-${mode.name}-$docId", factory = CameraViewModel.factory(container, mode, docId))
    val state by vm.state.collectAsState()

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(30)) { uris ->
        if (uris.isNotEmpty()) vm.importUris(uris)
    }

    LaunchedEffect(state.finishedDocId) {
        val id = state.finishedDocId ?: return@LaunchedEffect
        if (docId != null) {
            navController.popBackStack()
        } else {
            navController.navigate(Routes.document(id)) { popUpTo(Routes.CAMERA) { inclusive = true } }
        }
    }
    LaunchedEffect(state.errorRes) {
        val e = state.errorRes ?: return@LaunchedEffect
        toast(context, e)
        vm.clearError()
    }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) { onDispose { analysisExecutor.shutdown() } }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        val owner = context.findActivity() as? LifecycleOwner ?: return@LaunchedEffect
        val provider = context.awaitCameraProvider()
        val ratio = AspectRatioStrategy(AspectRatio.RATIO_4_3, AspectRatioStrategy.FALLBACK_RULE_AUTO)
        val preview = Preview.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
            .build().also { it.surfaceProvider = previewView.surfaceProvider }
        val capture = ImageCapture.Builder()
            .setResolutionSelector(ResolutionSelector.Builder().setAspectRatioStrategy(ratio).build())
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder().setAspectRatioStrategy(ratio)
                    .setResolutionStrategy(ResolutionStrategy(Size(960, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { proxy ->
            try {
                val raw = proxy.toBitmap()
                val rot = proxy.imageInfo.rotationDegrees
                val upright = if (rot == 0) raw else {
                    Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(rot.toFloat()) }, true)
                }
                vm.onFrame(upright)
                if (upright !== raw) raw.recycle()
                upright.recycle()
            } catch (e: Throwable) {
                // skip this frame
            } finally {
                proxy.close()
            }
        }
        provider.unbindAll()
        camera = provider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
        imageCapture = capture
    }
    DisposableEffect(hasPermission) {
        onDispose { runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() } }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }

    fun capture() {
        val ic = imageCapture ?: return
        if (state.busy) return
        val file = container.fileStore.newTempFile("jpg")
        ic.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) { vm.onCaptured(file) }
                override fun onError(exception: ImageCaptureException) { toast(context, R.string.camera_error_capture) }
            }
        )
    }
    LaunchedEffect(state.autoCaptureTick) { if (state.autoCaptureTick > 0) capture() }

    Column(Modifier.fillMaxSize().background(TayfBrown)) {
        TayfTopBar(
            title = stringResource(modeTitle(mode)),
            onBack = { navController.popBackStack() },
            actions = {
                TextButton(onClick = { torch = !torch }) {
                    Text(stringResource(if (torch) R.string.camera_torch_on else R.string.camera_torch_off), color = TayfGold)
                }
            }
        )
        if (!hasPermission) {
            EmptyState(stringResource(R.string.camera_permission_needed), TayfIcons.Camera, Modifier.weight(1f))
            TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(stringResource(R.string.camera_permission_grant), color = TayfGold)
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.fillMaxHeight().aspectRatio(3f / 4f).clip(RoundedCornerShape(12.dp))) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                    Overlay(state, Modifier.fillMaxSize())
                    Column(Modifier.align(Alignment.TopStart).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val q = state.quality
                        if (state.qualityGuard && q != null) {
                            if (q.isBlurry) Warn(stringResource(R.string.quality_blurry))
                            if (q.hasGlare) Warn(stringResource(R.string.quality_glare))
                            if (q.hasShadow) Warn(stringResource(R.string.quality_shadow))
                        }
                    }
                    Text(
                        text = hint(mode, state),
                        color = TayfText, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0x99000000)).padding(8.dp)
                    )
                }
            }
            if (state.stableProgress > 0f) {
                LinearProgressIndicator(
                    progress = { state.stableProgress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    color = TayfOk, trackColor = Color(0x33FFFFFF)
                )
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.size(56.dp)
                ) { Icon(TayfIcons.Gallery, stringResource(R.string.camera_import), tint = TayfGold, modifier = Modifier.size(30.dp)) }

                Shutter(
                    busy = state.busy,
                    recording = state.longActive,
                    onClick = {
                        if (mode == ScanMode.LONG) { if (state.longActive) vm.stopLong() else vm.startLong() } else capture()
                    }
                )

                if (mode == ScanMode.BATCH && state.capturedCount > 0) {
                    TextButton(onClick = { vm.finish() }) {
                        Text(stringResource(R.string.camera_done, state.capturedCount), color = TayfGold)
                    }
                } else {
                    Box(Modifier.size(56.dp))
                }
            }
        }
    }
}

@Composable
private fun hint(mode: ScanMode, state: CameraUiState): String = when {
    state.busy -> stringResource(R.string.camera_processing)
    mode == ScanMode.LONG && state.longActive -> stringResource(R.string.camera_long_moving, state.longFrames)
    mode == ScanMode.LONG -> stringResource(R.string.camera_long_hint)
    mode == ScanMode.ID_CARD && state.idStep == 0 -> stringResource(R.string.camera_id_front)
    mode == ScanMode.ID_CARD -> stringResource(R.string.camera_id_back)
    mode == ScanMode.BOOK -> stringResource(R.string.camera_book_hint)
    state.quad == null -> stringResource(R.string.camera_looking)
    else -> stringResource(R.string.camera_hold_steady)
}

@Composable
private fun Warn(text: String) {
    Text(
        text, color = TayfBrown, style = MaterialTheme.typography.labelMedium,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(TayfGoldLight).padding(horizontal = 10.dp, vertical = 3.dp)
    )
}

@Composable
private fun Shutter(busy: Boolean, recording: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(76.dp).clip(CircleShape).border(4.dp, TayfGold, CircleShape)
            .clickable(enabled = !busy, onClick = onClick).padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier.fillMaxSize().clip(if (recording) RoundedCornerShape(10.dp) else CircleShape)
                .background(if (busy) Color(0x66C9A24B) else if (recording) TayfDanger else TayfGold)
        )
    }
}

@Composable
private fun Overlay(state: CameraUiState, modifier: Modifier) {
    val q = state.quad ?: return
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(q.tlX * w, q.tlY * h)
            lineTo(q.trX * w, q.trY * h)
            lineTo(q.brX * w, q.brY * h)
            lineTo(q.blX * w, q.blY * h)
            close()
        }
        val color = if (state.stableProgress > 0.5f) TayfOk else TayfGold
        drawPath(path, color.copy(alpha = 0.18f))
        drawPath(path, color, style = Stroke(width = 5f))
        for (p in listOf(Offset(q.tlX * w, q.tlY * h), Offset(q.trX * w, q.trY * h), Offset(q.brX * w, q.brY * h), Offset(q.blX * w, q.blY * h))) {
            drawCircle(color, 9f, p)
        }
    }
}
