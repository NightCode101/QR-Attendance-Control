package cics.csup.qrattendancecontrol;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@androidx.camera.core.ExperimentalGetImage
public class CustomScanActivity extends AppCompatActivity {

    private static final String TAG = "CustomScanActivity";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    private PreviewView previewView;
    private Slider slider_zoom;
    private MaterialButton flashButton;
    private TextView titleText;
    private TextView scanner_indicator;

    private ExecutorService cameraExecutor;
    private Camera camera;
    private BarcodeScanner barcodeScanner;
    private boolean isFlashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_QRAttendanceControl);
        setContentView(R.layout.activity_custom_scan);

        previewView = findViewById(R.id.camera_preview);
        slider_zoom = findViewById(R.id.slider_zoom);
        flashButton = findViewById(R.id.button_toggle_flash);
        titleText = findViewById(R.id.scanner_title);
        scanner_indicator = findViewById(R.id.scanner_indicator);

        String title = getIntent().getStringExtra("SCAN_TITLE");
        String indicator = getIntent().getStringExtra("SCAN_INDICATOR");

        if (title != null) titleText.setText(title);
        if (indicator != null) scanner_indicator.setText(indicator);

        cameraExecutor = Executors.newSingleThreadExecutor();

        if (isCameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        }
    }

    private boolean isCameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @androidx.camera.core.ExperimentalGetImage
    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        imageAnalysis.setAnalyzer(cameraExecutor, image -> {
            android.media.Image mediaImage = image.getImage();
            if (mediaImage != null) {
                InputImage inputImage = InputImage.fromMediaImage(mediaImage, image.getImageInfo().getRotationDegrees());

                barcodeScanner.process(inputImage)
                        .addOnSuccessListener(barcodes -> {
                            handleBarcodes(barcodes);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "QR Code scanning failed", e);
                        })
                        .addOnCompleteListener(task -> {
                            mediaImage.close();
                            image.close();
                        });
            }
        });

        try {
            cameraProvider.unbindAll();
            camera = cameraProvider.bindToLifecycle(
                    (LifecycleOwner) this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );
            setupCameraControls();
        } catch (Exception e) {
            Log.e(TAG, "Use case binding failed", e);
        }
    }

    private void handleBarcodes(List<Barcode> barcodes) {
        if (barcodes.isEmpty()) return;

        for (Barcode barcode : barcodes) {
            String rawValue = barcode.getRawValue();
            if (rawValue != null) {
                cameraExecutor.shutdown();
                barcodeScanner.close();

                Intent resultIntent = new Intent();
                resultIntent.putExtra("SCAN_RESULT", rawValue);
                setResult(RESULT_OK, resultIntent);

                finish();
                return;
            }
        }
    }

    private void setupCameraControls() {
        if (camera.getCameraInfo().hasFlashUnit()) {
            flashButton.setVisibility(View.VISIBLE);
            flashButton.setOnClickListener(v -> {
                isFlashOn = !isFlashOn;
                camera.getCameraControl().enableTorch(isFlashOn);
            });
        } else {
            flashButton.setVisibility(View.GONE);
        }

        if (camera.getCameraInfo().getZoomState().getValue() != null) {
            float minZoom = camera.getCameraInfo().getZoomState().getValue().getMinZoomRatio();
            float maxZoom = camera.getCameraInfo().getZoomState().getValue().getMaxZoomRatio();

            if (minZoom < maxZoom) {
                slider_zoom.setVisibility(View.VISIBLE);
                slider_zoom.setValueFrom(0.0f);
                slider_zoom.setValueTo(1.0f);
                slider_zoom.setStepSize(0.1f);
                slider_zoom.setValue(0.0f);

                slider_zoom.addOnChangeListener((slider, value, fromUser) -> {
                    try {
                        camera.getCameraControl().setLinearZoom(value);
                    } catch (Exception e) {
                        Log.e("CustomScanActivity", "Zoom failed: " + e.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (barcodeScanner != null) {
            barcodeScanner.close();
        }
    }
}