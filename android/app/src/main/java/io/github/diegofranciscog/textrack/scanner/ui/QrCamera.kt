package io.github.diegofranciscog.textrack.scanner.ui

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Vista de cámara (CameraX) con análisis de QR por ML Kit en el propio dispositivo (modelo empaquetado:
 * funciona sin Internet). Ignora el mismo código durante 3 s para no registrar ráfagas del mismo ticket.
 */
@Composable
fun QrCamera(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnCode = rememberUpdatedState(onCode)
    val previewView = remember { PreviewView(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var lastCode: String? = null
        var lastAt = 0L
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { useCase ->
                useCase.setAnalyzer(executor) { proxy ->
                    analyze(proxy, scanner) { code ->
                        val now = System.currentTimeMillis()
                        if (code != lastCode || now - lastAt > 3_000) {
                            lastCode = code
                            lastAt = now
                            ContextCompat.getMainExecutor(context).execute { latestOnCode.value(code) }
                        }
                    }
                }
            }
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            provider.unbindAll()
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            runCatching { providerFuture.get().unbindAll() }
            analysis.clearAnalyzer()
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            scanner.close()
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(
    proxy: androidx.camera.core.ImageProxy,
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    onCode: (String) -> Unit,
) {
    val image = proxy.image
    if (image == null) {
        proxy.close()
        return
    }
    scanner.process(InputImage.fromMediaImage(image, proxy.imageInfo.rotationDegrees))
        .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode) }
        .addOnCompleteListener { proxy.close() }
}
