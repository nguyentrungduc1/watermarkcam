package com.nguyentrungduc1.watermarkcam;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureFailure;
import android.util.Range;
import android.util.Log;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Size;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.camera2.interop.Camera2CameraInfo;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.CameraEffect;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.effects.OverlayEffect;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.video.FallbackStrategy;
import androidx.camera.video.MediaStoreOutputOptions;
import androidx.camera.video.PendingRecording;
import androidx.camera.video.Quality;
import androidx.camera.video.QualitySelector;
import androidx.camera.video.Recorder;
import androidx.camera.video.Recording;
import androidx.camera.video.VideoCapture;
import androidx.camera.video.VideoRecordEvent;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.slider.Slider;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import androidx.exifinterface.media.ExifInterface;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private TextureView photoPreviewView;
    private Button shutterButton;
    private ImageButton galleryButton;
    private TextView flashButton;
    private TextView switchButton;
    private TextView photoMode;
    private TextView videoMode;
    private TextView smoothButton;
    private TextView brightButton;
    private TextView watermarkTime;
    private TextView watermarkDate;
    private TextView watermarkWeekday;
    private TextView zoomText;
    private TextView timerText;
    private TextView statusText;
    private View focusRing;
    private View screenFlashView;
    private View beautyPanel;
    private View watermarkOverlay;
    private Slider zoomSlider;

    private ExecutorService cameraExecutor;
    private ImageCapture imageCapture;
    private VideoCapture<Recorder> videoCapture;
    private OverlayEffect videoOverlayEffect;
    private static HandlerThread videoEffectThread;
    private Size photoPreviewSize;
    private Preview videoPreview;
    private int cameraGeneration;
    private boolean activityResumed;
    private boolean recordingStopping;
    private final Runnable refreshVideoRotation = () -> {
        if (activityResumed && this.isVideoMode && this.activeRecording == null) startCamera();
    };
    private androidx.constraintlayout.widget.ConstraintSet portraitConstraints;
    private Runnable pendingPhoto;
    private boolean precaptureStarted;
    private long precaptureDeadline;
    private final CameraCaptureSession.CaptureCallback photoMetering = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            if (pendingPhoto == null) return;
            Integer ae = result.get(CaptureResult.CONTROL_AE_STATE);
            if (ae != null && ae == CaptureResult.CONTROL_AE_STATE_PRECAPTURE) precaptureStarted = true;
            if ((precaptureStarted && (ae == null || ae != CaptureResult.CONTROL_AE_STATE_PRECAPTURE))
                    || System.currentTimeMillis() >= precaptureDeadline) finishPrecapture();
        }
    };

    private void finishPrecapture() {
        Runnable capture = pendingPhoto;
        pendingPhoto = null;
        if (capture != null) capture.run();
    }
    private Camera camera;
    private android.hardware.Camera photoCamera;
    private int photoCameraId = -1;
    private CameraDevice photoCameraDevice;
    private CameraCaptureSession photoCaptureSession;
    private ImageReader photoImageReader;
    private HandlerThread photoCameraThread;
    private Handler photoCameraHandler;
    private String photoCamera2Id;
    private List<String> photoCamera2Candidates;
    private int photoCamera2CandidateIndex;
    private Surface photoPreviewSurface;
    private Recording activeRecording;
    private Uri lastSavedUri;
    private boolean isVideoMode;
    private boolean usingBackCamera = true;
    private boolean smoothSkinEnabled = true;
    private boolean brightenSkinEnabled = true;
    private boolean restartingFromResume;
    private boolean skipNextResumeStart;
    private int flashMode = ImageCapture.FLASH_MODE_AUTO;
    private long recordingStartedAt;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable hideStatusRunnable = this::hideStatus;
    private ScaleGestureDetector scaleDetector;
    private final Runnable watermarkTick = new Runnable() {
        @Override
        public void run() {
            updateWatermarkText(new Date());
            timerHandler.postDelayed(this, 1000);
        }
    };

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                if (hasAllPermissions()) {
                    loadLatestThumbnail();
                    updateWatermarkText(new Date());
                    timerHandler.post(watermarkTick);
                    startCamera();
                } else {
                    showStatus("Cần quyền Camera và Micro để chụp ảnh, quay video.");
                }
            });

    private final Runnable timerTick = new Runnable() {
        @Override
        public void run() {
            long elapsedSeconds = (System.currentTimeMillis() - recordingStartedAt) / 1000;
            timerText.setText(String.format(Locale.US, "%02d:%02d", elapsedSeconds / 60, elapsedSeconds % 60));
            timerHandler.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        hideSystemChrome();

        cameraExecutor = Executors.newSingleThreadExecutor();
        bindViews();
        portraitConstraints = new androidx.constraintlayout.widget.ConstraintSet();
        portraitConstraints.clone((androidx.constraintlayout.widget.ConstraintLayout) findViewById(R.id.root));
        setupUi();
        updateOrientationLayout();

        if (hasAllPermissions()) {
            loadLatestThumbnail();
            updateWatermarkText(new Date());
            timerHandler.post(watermarkTick);
            skipNextResumeStart = true;
            startCamera();
        } else {
            permissionLauncher.launch(requiredPermissions());
        }
    }

    private void bindViews() {
        previewView = findViewById(R.id.previewView);
        photoPreviewView = findViewById(R.id.photoPreviewView);
        shutterButton = findViewById(R.id.shutterButton);
        galleryButton = findViewById(R.id.galleryButton);
        flashButton = findViewById(R.id.flashButton);
        switchButton = findViewById(R.id.switchButton);
        photoMode = findViewById(R.id.photoMode);
        videoMode = findViewById(R.id.videoMode);
        smoothButton = findViewById(R.id.smoothButton);
        brightButton = findViewById(R.id.brightButton);
        watermarkTime = findViewById(R.id.watermarkTime);
        watermarkDate = findViewById(R.id.watermarkDate);
        watermarkWeekday = findViewById(R.id.watermarkWeekday);
        zoomText = findViewById(R.id.zoomText);
        timerText = findViewById(R.id.timerText);
        statusText = findViewById(R.id.statusText);
        focusRing = findViewById(R.id.focusRing);
        screenFlashView = findViewById(R.id.screenFlashView);
        beautyPanel = findViewById(R.id.beautyPanel);
        watermarkOverlay = findViewById(R.id.watermarkOverlay);
        zoomSlider = findViewById(R.id.zoomSlider);
    }

    private void setupUi() {
        updateModeTabs();
        updateFlashButton();
        updateBeautyUi();

        photoPreviewView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
                if (!isVideoMode && hasAllPermissions()) startCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
                updatePhotoTransform();
            }

            @Override
            public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
                releasePhotoCamera();
                releasePhotoCamera2();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {
            }
        });

        shutterButton.setOnClickListener(v -> {
            if (isVideoMode) {
                toggleRecording();
            } else {
                takePhoto();
            }
        });

        photoMode.setOnClickListener(v -> setVideoMode(false));
        videoMode.setOnClickListener(v -> setVideoMode(true));
        switchButton.setOnClickListener(v -> {
            if (activeRecording != null) return;
            usingBackCamera = !usingBackCamera;
            updateBeautyUi();
            startCamera();
        });
        flashButton.setOnClickListener(v -> cycleFlashMode());
        galleryButton.setOnClickListener(v -> openLastCapture());
        smoothButton.setOnClickListener(v -> {
            smoothSkinEnabled = !smoothSkinEnabled;
            updateBeautyUi();
        });
        brightButton.setOnClickListener(v -> {
            brightenSkinEnabled = !brightenSkinEnabled;
            updateBeautyUi();
        });

        zoomSlider.addOnChangeListener((slider, value, fromUser) -> {
            if (fromUser && !isVideoMode && photoCamera != null) {
                setPhotoZoom(value);
            } else if (fromUser && camera != null) {
                camera.getCameraControl().setZoomRatio(value);
                zoomText.setText(String.format(Locale.US, "%.1fx", value));
            }
        });

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(@NonNull ScaleGestureDetector detector) {
                float current = zoomSlider.getValue();
                float target = clamp(current * detector.getScaleFactor(), zoomSlider.getValueFrom(), zoomSlider.getValueTo());
                if (!isVideoMode && photoCamera != null) {
                    setPhotoZoom(target);
                } else if (camera != null) {
                    camera.getCameraControl().setZoomRatio(target);
                } else {
                    return false;
                }
                zoomSlider.setValue(target);
                zoomText.setText(String.format(Locale.US, "%.1fx", target));
                return true;
            }
        });

        View.OnTouchListener previewTouchListener = (view, event) -> {
            scaleDetector.onTouchEvent(event);
            if (event.getAction() == MotionEvent.ACTION_UP && !scaleDetector.isInProgress()) {
                focusAt(event.getX(), event.getY());
            }
            return true;
        };
        previewView.setOnTouchListener(previewTouchListener);
        photoPreviewView.setOnTouchListener(previewTouchListener);
    }

    @androidx.annotation.OptIn(markerClass = androidx.camera.camera2.interop.ExperimentalCamera2Interop.class)
    private void startCamera() {
        if (!hasAllPermissions()) return;
        final int generation = ++cameraGeneration;
        timerHandler.removeCallbacks(refreshVideoRotation);
        if (!isVideoMode) {
            startPhotoCamera();
            return;
        }
        releasePhotoCamera();
        releasePhotoCamera2();
        photoPreviewView.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            if (generation != cameraGeneration || !isVideoMode) return;
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                provider.unbindAll();
                closeVideoOverlayEffect();
                camera = null;
                videoCapture = null;
                int lensFacing = usingBackCamera ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
                Preview preview = new Preview.Builder()
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .build();
                videoPreview = preview;
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                Size largestJpegSize = findLargestJpegSize(lensFacing);
                ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setFlashMode(flashMode)
                        .setTargetRotation(previewView.getDisplay().getRotation())
                        .setJpegQuality(100);
                if (largestJpegSize != null) {
                    imageCaptureBuilder.setTargetResolution(largestJpegSize);
                }
                imageCapture = imageCaptureBuilder.build();

                Recorder recorder = new Recorder.Builder()
                        .setQualitySelector(QualitySelector.fromOrderedList(
                                Arrays.asList(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
                                FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD)))
                        .build();
                videoCapture = new VideoCapture.Builder<>(recorder)
                        .setTargetFrameRate(new Range<>(30, 30))
                        .setTargetRotation(previewView.getDisplay().getRotation()).build();
                videoOverlayEffect = createVideoWatermarkEffect();

                final boolean back = usingBackCamera;
                CameraSelector selector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .addCameraFilter(infos -> {
                            if (!back) return new ArrayList<>(infos);
                            List<CameraInfo> main = new ArrayList<>();
                            for (CameraInfo info : infos) {
                                if ("0".equals(Camera2CameraInfo.from(info).getCameraId())) main.add(info);
                            }
                            return main;
                        })
                        .build();

                UseCaseGroup group = new UseCaseGroup.Builder()
                        .addUseCase(preview)
                        .addUseCase(videoCapture)
                        .addEffect(videoOverlayEffect)
                        .build();
                camera = provider.bindToLifecycle(this, selector, group);
                configureZoom();
                applyVideoTorch();
                hideStatus();
            } catch (Exception exception) {
                Log.e("LucidCamera", "Video bind failed", exception);
                closeVideoOverlayEffect();
                showStatus("Không mở được camera: " + exception.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void updatePhotoTransform() {
        if (photoPreviewSize == null || photoPreviewView.getWidth() == 0) return;
        float w = photoPreviewView.getWidth(), h = photoPreviewView.getHeight();
        int rotation = photoPreviewView.getDisplay().getRotation();
        float degrees = rotation * 90f;
        float naturalW = photoPreviewSize.getHeight(), naturalH = photoPreviewSize.getWidth();
        boolean sideways = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        float scale = Math.max(w / (sideways ? naturalH : naturalW), h / (sideways ? naturalW : naturalH));
        Matrix matrix = new Matrix();
        matrix.setScale(naturalW / w, naturalH / h, w / 2, h / 2);
        matrix.postRotate(-degrees, w / 2, h / 2);
        matrix.postScale(scale, scale, w / 2, h / 2);
        photoPreviewView.setTransform(matrix);
    }

    @Override
    public void onConfigurationChanged(@NonNull android.content.res.Configuration configuration) {
        super.onConfigurationChanged(configuration);
        updateOrientationLayout();
        photoPreviewView.post(this::updatePhotoTransform);
        scheduleVideoRotationRefresh();
    }

    private void scheduleVideoRotationRefresh() {
        timerHandler.removeCallbacks(refreshVideoRotation);
        if (isVideoMode && activeRecording == null)
            timerHandler.postDelayed(refreshVideoRotation, 250);
    }

    private void updateOrientationLayout() {
        if (portraitConstraints == null) return;
        androidx.constraintlayout.widget.ConstraintLayout root = findViewById(R.id.root);
        androidx.constraintlayout.widget.ConstraintSet c = new androidx.constraintlayout.widget.ConstraintSet();
        c.clone(portraitConstraints);
        boolean landscape = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        if (landscape) {
            c.clear(R.id.shutterButton, 6);
            c.connect(R.id.shutterButton, 7, 0, 7, dp(40));
            c.clear(R.id.modeTabs, 6);
            c.connect(R.id.modeTabs, 7, 0, 7, dp(6));
            c.connect(R.id.zoomPanel, 7, R.id.galleryButton, 6, dp(12));
            c.connect(R.id.zoomPanel, 6, R.id.watermarkOverlay, 7, dp(8));
            c.setMargin(R.id.zoomPanel, 4, dp(24));
            c.setMargin(R.id.watermarkOverlay, 4, dp(22));
            c.setMargin(R.id.watermarkOverlay, 6, dp(12));
            c.clear(R.id.galleryButton, 6);
            c.connect(R.id.galleryButton, 7, R.id.shutterButton, 6, dp(8));
        }
        c.setVisibility(R.id.beautyPanel, !usingBackCamera && !isVideoMode ? View.VISIBLE : View.GONE);
        c.setVisibility(R.id.previewView, isVideoMode ? View.VISIBLE : View.GONE);
        c.setVisibility(R.id.photoPreviewView, isVideoMode ? View.GONE : View.VISIBLE);
        c.setVisibility(R.id.timerText, activeRecording != null ? View.VISIBLE : View.GONE);
        c.setVisibility(R.id.statusText, statusText.getVisibility());
        c.applyTo(root);
    }

    private void startPhotoCamera() {
        final int generation = cameraGeneration;
        previewView.setVisibility(View.GONE);
        photoPreviewView.setVisibility(View.VISIBLE);
        if (!photoPreviewView.isAvailable()) return;
        closeVideoOverlayEffect();
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            if (generation != cameraGeneration || isVideoMode) return;
            try {
                ProcessCameraProvider provider = cameraProviderFuture.get();
                provider.unbindAll();
            } catch (Exception ignored) {
            }
            camera = null;
            videoCapture = null;
            videoPreview = null;
            imageCapture = null;
            startPhotoCamera2();
        }, ContextCompat.getMainExecutor(this));
    }

    private void startPhotoCamera2() {
        releasePhotoCamera2();
        startPhotoThread();
        try {
            int lensFacing = usingBackCamera ? CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
            photoCamera2Candidates = findCamera2Candidates(lensFacing);
            photoCamera2CandidateIndex = 0;
            photoCamera2Id = photoCamera2Candidates.isEmpty() ? findCamera2Id(lensFacing) : photoCamera2Candidates.get(0);
            openCurrentPhotoCamera2();
        } catch (Exception exception) {
            startLegacyPhotoCamera();
        }
    }

    private void openCurrentPhotoCamera2() {
        try {
            Size photoSize = findLargestJpegSizeForId(photoCamera2Id);
            Log.i("LucidCamera", "Photo camera=" + photoCamera2Id + " JPEG=" + photoSize);
            Size previewSize = bestCamera2PreviewSize(photoCamera2Id);
            if (photoSize == null) photoSize = new Size(1920, 1080);
            if (previewSize == null) previewSize = new Size(1920, 1080);
            photoPreviewSize = previewSize;
            runOnUiThread(this::updatePhotoTransform);

            SurfaceTexture texture = photoPreviewView.getSurfaceTexture();
            texture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            photoPreviewSurface = new Surface(texture);
            photoImageReader = ImageReader.newInstance(photoSize.getWidth(), photoSize.getHeight(), ImageFormat.JPEG, 2);
            photoImageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image == null) return;
                byte[] bytes = new byte[image.getPlanes()[0].getBuffer().remaining()];
                image.getPlanes()[0].getBuffer().get(bytes);
                image.close();
                Uri savedUri = saveJpeg(bytes, 0);
                runOnUiThread(() -> {
                    shutterButton.setEnabled(true);
                    if (savedUri != null) {
                        lastSavedUri = savedUri;
                        galleryButton.setImageURI(lastSavedUri);
                        showTransientStatus("Đã lưu ảnh");
                    } else {
                        showStatus("Không lưu được ảnh");
                    }
                });
            }, photoCameraHandler);

            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            manager.openCamera(photoCamera2Id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice cameraDevice) {
                    photoCameraDevice = cameraDevice;
                    createPhotoSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                    cameraDevice.close();
                    photoCameraDevice = null;
                }

                @Override
                public void onError(@NonNull CameraDevice cameraDevice, int error) {
                    cameraDevice.close();
                    photoCameraDevice = null;
                    openNextPhotoCameraOrFallback(error);
                }
            }, photoCameraHandler);
            configurePhotoZoom();
            hideStatus();
        } catch (Exception exception) {
            openNextPhotoCameraOrFallback(-1);
        }
    }

    private void openNextPhotoCameraOrFallback(int lastError) {
        releasePhotoCamera2();
        startPhotoThread();
        if (photoCamera2Candidates != null && photoCamera2CandidateIndex + 1 < photoCamera2Candidates.size()) {
            photoCamera2CandidateIndex++;
            photoCamera2Id = photoCamera2Candidates.get(photoCamera2CandidateIndex);
            openCurrentPhotoCamera2();
            return;
        }
        runOnUiThread(() -> {
            startLegacyPhotoCamera();
            showTransientStatus(lastError > 0 ? "Camera2 lỗi " + lastError + ", dùng camera dự phòng" : "Dùng camera dự phòng");
        });
    }

    private void createPhotoSession() {
        if (photoCameraDevice == null || photoPreviewSurface == null || photoImageReader == null) return;
        try {
            photoCameraDevice.createCaptureSession(
                    Arrays.asList(photoPreviewSurface, photoImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            photoCaptureSession = session;
                            try {
                                CaptureRequest.Builder request = photoCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                request.addTarget(photoPreviewSurface);
                                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                applyCamera2Flash(request, false);
                                session.setRepeatingRequest(request.build(), photoMetering, photoCameraHandler);
                            } catch (Exception exception) {
                                runOnUiThread(() -> showStatus("Preview lỗi: " + exception.getMessage()));
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            runOnUiThread(() -> showStatus("Không cấu hình được camera ảnh"));
                        }
                    },
                    photoCameraHandler
            );
        } catch (Exception exception) {
            runOnUiThread(() -> showStatus("Session lỗi: " + exception.getMessage()));
        }
    }

    private void restartCamera2Preview() {
        if (photoCameraDevice == null || photoCaptureSession == null || photoPreviewSurface == null) return;
        try {
            CaptureRequest.Builder request = photoCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            request.addTarget(photoPreviewSurface);
            request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            applyCamera2Flash(request, false);
            photoCaptureSession.setRepeatingRequest(request.build(), photoMetering, photoCameraHandler);
        } catch (Exception ignored) {
        }
    }

    private void applyCamera2Flash(CaptureRequest.Builder request, boolean stillCapture) {
        if (!usingBackCamera) {
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            return;
        }
        if (flashMode == ImageCapture.FLASH_MODE_ON) {
            setCamera2AeMode(request, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        } else if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            if (isCamera2AeModeAvailable(CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH)) {
                request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            } else {
                request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            }
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        } else {
            request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
            request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
        }
    }

    private void setCamera2AeMode(CaptureRequest.Builder request, int requestedMode) {
        request.set(CaptureRequest.CONTROL_AE_MODE,
                isCamera2AeModeAvailable(requestedMode) ? requestedMode : CaptureRequest.CONTROL_AE_MODE_ON);
    }

    private boolean isCamera2AeModeAvailable(int requestedMode) {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            int[] modes = manager.getCameraCharacteristics(photoCamera2Id)
                    .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES);
            if (modes == null) return false;
            for (int mode : modes) {
                if (mode == requestedMode) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void applyVideoTorch() {
        if (camera == null) return;
        boolean torch = activeRecording != null && isVideoMode && usingBackCamera && flashMode == ImageCapture.FLASH_MODE_ON;
        camera.getCameraControl().enableTorch(torch);
    }

    private void startLegacyPhotoCamera() {
        releasePhotoCamera();
        try {
            photoCameraId = findLegacyCameraId(usingBackCamera);
            photoCamera = android.hardware.Camera.open(photoCameraId);
            android.hardware.Camera.Parameters params = photoCamera.getParameters();
            android.hardware.Camera.Size pictureSize = largestLegacySize(params.getSupportedPictureSizes());
            android.hardware.Camera.Size previewSize = bestLegacyPreviewSize(params.getSupportedPreviewSizes());
            if (pictureSize != null) params.setPictureSize(pictureSize.width, pictureSize.height);
            if (previewSize != null) params.setPreviewSize(previewSize.width, previewSize.height);
            if (params.getSupportedFocusModes().contains(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(android.hardware.Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            params.setJpegQuality(100);
            params.setRotation(photoRotation());
            photoCamera.setParameters(params);
            applyLegacyFlashMode();
            photoCamera.setDisplayOrientation(displayOrientation(photoCameraId));
            photoCamera.setPreviewTexture(photoPreviewView.getSurfaceTexture());
            photoCamera.startPreview();
            configurePhotoZoom();
            hideStatus();
        } catch (Exception exception) {
            showStatus("Không mở được camera ảnh: " + exception.getMessage());
        }
    }

    private void configureZoom() {
        if (camera == null) return;
        CameraInfo info = camera.getCameraInfo();
        float minZoom = info.getZoomState().getValue().getMinZoomRatio();
        float maxZoom = Math.min(info.getZoomState().getValue().getMaxZoomRatio(), 10f);
        zoomSlider.setValueFrom(minZoom);
        zoomSlider.setValueTo(maxZoom);
        zoomSlider.setValue(1f < minZoom ? minZoom : 1f);
        zoomText.setText(String.format(Locale.US, "%.1fx", zoomSlider.getValue()));
    }

    private void configurePhotoZoom() {
        if (photoCameraDevice != null) {
            zoomSlider.setValueFrom(1f);
            zoomSlider.setValueTo(8f);
            zoomSlider.setValue(1f);
            zoomText.setText("1.0x");
            return;
        }
        if (photoCamera == null) return;
        android.hardware.Camera.Parameters params = photoCamera.getParameters();
        if (!params.isZoomSupported()) {
            zoomSlider.setValueFrom(1f);
            zoomSlider.setValueTo(1f);
            zoomSlider.setValue(1f);
            zoomText.setText("1.0x");
            return;
        }
        float max = Math.max(1f, Math.min(10f, params.getMaxZoom() / 10f + 1f));
        zoomSlider.setValueFrom(1f);
        zoomSlider.setValueTo(max);
        zoomSlider.setValue(1f);
        zoomText.setText("1.0x");
    }

    private void setPhotoZoom(float ratio) {
        if (photoCameraDevice != null) {
            zoomText.setText(String.format(Locale.US, "%.1fx", ratio));
            return;
        }
        if (photoCamera == null) return;
        android.hardware.Camera.Parameters params = photoCamera.getParameters();
        if (!params.isZoomSupported()) return;
        int maxZoom = params.getMaxZoom();
        int zoom = Math.round((ratio - 1f) / Math.max(1f, zoomSlider.getValueTo() - 1f) * maxZoom);
        params.setZoom(Math.max(0, Math.min(maxZoom, zoom)));
        photoCamera.setParameters(params);
        zoomText.setText(String.format(Locale.US, "%.1fx", ratio));
    }

    private void takePhoto() {
        if (!isVideoMode && photoCameraDevice != null && photoCaptureSession != null) {
            takeCamera2Photo();
            return;
        }
        if (!isVideoMode && photoCamera != null) {
            takeLegacyPhoto();
            return;
        }
        if (imageCapture == null) return;

        String name = "IMG_" + timestamp();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lucid Camera");
        }

        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                values
        ).build();

        shutterButton.setEnabled(false);
        Runnable capture = () -> imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                shutterButton.setEnabled(true);
                lastSavedUri = outputFileResults.getSavedUri();
                galleryButton.setImageURI(lastSavedUri);
                showTransientStatus("Đã lưu ảnh");
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                shutterButton.setEnabled(true);
                showStatus("Chụp ảnh lỗi: " + exception.getMessage());
            }
        });
        if (!usingBackCamera && flashMode != ImageCapture.FLASH_MODE_OFF) {
            runScreenFlash(capture);
        } else {
            capture.run();
        }
    }

    private void takeCamera2Photo() {
        shutterButton.setEnabled(false);
        Runnable capture = () -> {
            try {
                CaptureRequest.Builder request = photoCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                request.addTarget(photoImageReader.getSurface());
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                applyCamera2Flash(request, true);
                request.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
                applyStillQuality(request);
                request.set(CaptureRequest.JPEG_ORIENTATION, camera2JpegOrientation());
                CameraCaptureSession session = photoCaptureSession;
                Runnable still = () -> {
                    try {
                        session.capture(request.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureCompleted(@NonNull CameraCaptureSession s, @NonNull CaptureRequest r,
                                                           @NonNull TotalCaptureResult result) {
                                Log.i("LucidCamera", "Still flashState=" + result.get(CaptureResult.FLASH_STATE));
                                restartCamera2Preview();
                            }
                            @Override
                            public void onCaptureFailed(@NonNull CameraCaptureSession s, @NonNull CaptureRequest r,
                                                        @NonNull CaptureFailure failure) {
                                runOnUiThread(() -> { shutterButton.setEnabled(true); showStatus("Chụp ảnh lỗi"); });
                                restartCamera2Preview();
                            }
                        }, photoCameraHandler);
                    } catch (Exception error) {
                        runOnUiThread(() -> { shutterButton.setEnabled(true); showStatus(error.getMessage()); });
                    }
                };
                if (usingBackCamera && flashMode != ImageCapture.FLASH_MODE_OFF) {
                    pendingPhoto = still;
                    precaptureStarted = false;
                    precaptureDeadline = System.currentTimeMillis() + 2500;
                    CaptureRequest.Builder meter = photoCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                    meter.addTarget(photoPreviewSurface);
                    meter.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                    applyCamera2Flash(meter, false);
                    meter.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                    session.capture(meter.build(), photoMetering, photoCameraHandler);
                    photoCameraHandler.postDelayed(() -> { if (pendingPhoto == still) finishPrecapture(); }, 2700);
                } else still.run();
            } catch (Exception exception) {
                shutterButton.setEnabled(true);
                showStatus("Chụp ảnh lỗi: " + exception.getMessage());
            }
        };
        if (!usingBackCamera && flashMode != ImageCapture.FLASH_MODE_OFF) {
            runScreenFlash(capture);
        } else {
            capture.run();
        }
    }

    private void takeLegacyPhoto() {
        shutterButton.setEnabled(false);
        Runnable capture = () -> {
            try {
                photoCamera.takePicture(null, null, (data, camera) -> {
                    Uri savedUri = saveJpeg(data, photoRotation());
                    shutterButton.setEnabled(true);
                    if (savedUri != null) {
                        lastSavedUri = savedUri;
                        galleryButton.setImageURI(lastSavedUri);
                        showTransientStatus("Đã lưu ảnh");
                    } else {
                        showStatus("Không lưu được ảnh");
                    }
                    try {
                        camera.startPreview();
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception exception) {
                shutterButton.setEnabled(true);
                showStatus("Chụp ảnh lỗi: " + exception.getMessage());
            }
        };
        if (!usingBackCamera && flashMode != ImageCapture.FLASH_MODE_OFF) {
            runScreenFlash(capture);
        } else {
            capture.run();
        }
    }

    private Uri saveJpeg(byte[] data, int rotationDegrees) {
        byte[] finalData = processPhotoBytes(data, rotationDegrees);
        String name = "IMG_" + timestamp();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Lucid Camera");
        }
        Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
        if (uri == null) return null;
        try (OutputStream stream = getContentResolver().openOutputStream(uri)) {
            if (stream == null) return null;
            stream.write(finalData);
            return uri;
        } catch (Exception exception) {
            getContentResolver().delete(uri, null, null);
            return null;
        }
    }

    private byte[] processPhotoBytes(byte[] data, int rotationDegrees) {
        try {
            ExifInterface exif = new ExifInterface(new ByteArrayInputStream(data));
            if (exif.hasAttribute(ExifInterface.TAG_ORIENTATION)) rotationDegrees = exif.getRotationDegrees();
        } catch (java.io.IOException ignored) {
        }
        Bitmap source = BitmapFactory.decodeByteArray(data, 0, data.length);
        if (source == null) return data;
        Bitmap output = rotateBitmap(source, rotationDegrees);

        if (!usingBackCamera && (smoothSkinEnabled || brightenSkinEnabled)) {
            output = applySelfieBeauty(output);
        }
        drawWatermark(output, new Date());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        output.compress(Bitmap.CompressFormat.JPEG, 100, bytes);
        output.recycle();
        return bytes.toByteArray();
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int degrees) {
        int normalized = ((degrees % 360) + 360) % 360;
        Matrix matrix = new Matrix();
        matrix.postRotate(normalized);
        Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        Bitmap mutable = rotated.isMutable() ? rotated : rotated.copy(Bitmap.Config.ARGB_8888, true);
        if (rotated != mutable) rotated.recycle();
        if (bitmap != rotated) bitmap.recycle();
        return mutable;
    }

    private Bitmap applySelfieBeauty(Bitmap bitmap) {
        return SelfieRetouch.apply(getApplicationContext(), bitmap, smoothSkinEnabled, brightenSkinEnabled);
    }

    private void drawWatermark(Bitmap bitmap, Date date) {
        Canvas canvas = new Canvas(bitmap);
        float scale = bitmap.getWidth() / 1080f * 0.9f;
        float marginLeft = 44f * scale;
        float marginBottom = 60f * scale;
        float logoHeight = 120f * scale;
        float logoGap = 10f * scale;
        float timeSize = 100f * scale;
        float smallSize = 42f * scale;
        float barWidth = 6f * scale;
        float gapAfterTime = 22f * scale;
        float gapBeforeDate = 22f * scale;
        float rowGap = 48f * scale;

        Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        timePaint.setColor(Color.WHITE);
        timePaint.setTextSize(timeSize);
        timePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        timePaint.setShadowLayer(7f * scale, 2f * scale, 2f * scale, Color.BLACK);

        Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        smallPaint.setColor(Color.WHITE);
        smallPaint.setTextSize(smallSize);
        smallPaint.setTypeface(android.graphics.Typeface.DEFAULT);
        smallPaint.setShadowLayer(5f * scale, 2f * scale, 2f * scale, Color.BLACK);

        String time = new SimpleDateFormat("HH:mm", new Locale("vi", "VN")).format(date);
        String dateText = new SimpleDateFormat("dd/MM/yyyy", new Locale("vi", "VN")).format(date);
        String weekday = capitalizeWords(new SimpleDateFormat("EEEE", new Locale("vi", "VN")).format(date));

        Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
        Paint.FontMetrics smallMetrics = smallPaint.getFontMetrics();
        float smallLineHeight = smallMetrics.descent - smallMetrics.ascent;
        float textBlockHeight = smallLineHeight + rowGap;
        float textBaselineTop = bitmap.getHeight() - marginBottom - rowGap;
        float textBaselineBottom = bitmap.getHeight() - marginBottom;
        float timeBaseline = bitmap.getHeight() - marginBottom - textBlockHeight / 2f - (timeMetrics.ascent + timeMetrics.descent) / 2f;
        float timeWidth = timePaint.measureText(time);
        float barLeft = marginLeft + timeWidth + gapAfterTime;
        float barTop = textBaselineTop + smallMetrics.ascent;
        float barBottom = textBaselineBottom + smallMetrics.descent;
        float infoLeft = barLeft + barWidth + gapBeforeDate;

        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_watermark);
        if (logo != null) {
            float logoWidth = logo.getWidth() * (logoHeight / logo.getHeight());
            float logoTop = barTop - logoGap - logoHeight;
            canvas.drawBitmap(logo, null, new android.graphics.RectF(marginLeft, logoTop, marginLeft + logoWidth, logoTop + logoHeight), null);
            drawWatermarkLocation(canvas, marginLeft + logoWidth + 20f * scale, logoTop, logoHeight, scale);
            logo.recycle();
        }

        canvas.drawText(time, marginLeft, timeBaseline, timePaint);
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#FF7A00"));
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barBottom, barPaint);
        canvas.drawText(dateText, infoLeft, textBaselineTop, smallPaint);
        canvas.drawText(weekday, infoLeft, textBaselineBottom, smallPaint);
    }

    private void toggleRecording() {
        if (activeRecording != null) {
            if (!recordingStopping) {
                recordingStopping = true;
                activeRecording.stop();
            }
            return;
        }
        if (videoCapture == null) return;

        String name = "VID_" + timestamp();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Video.Media.DISPLAY_NAME, name);
        values.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Lucid Camera");
        }

        MediaStoreOutputOptions outputOptions = new MediaStoreOutputOptions.Builder(
                getContentResolver(),
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build();

        PendingRecording pendingRecording = videoCapture.getOutput().prepareRecording(this, outputOptions);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pendingRecording = pendingRecording.withAudioEnabled();
        }

        activeRecording = pendingRecording.start(ContextCompat.getMainExecutor(this), event -> {
            if (event instanceof VideoRecordEvent.Start) {
                recordingStartedAt = System.currentTimeMillis();
                timerText.setVisibility(View.VISIBLE);
                timerHandler.post(timerTick);
                shutterButton.setBackgroundResource(R.drawable.bg_shutter_video);
                applyVideoTorch();
                showTransientStatus("Đang quay video");
            } else if (event instanceof VideoRecordEvent.Finalize) {
                VideoRecordEvent.Finalize finalizeEvent = (VideoRecordEvent.Finalize) event;
                timerHandler.removeCallbacks(timerTick);
                timerText.setVisibility(View.GONE);
                shutterButton.setBackgroundResource(R.drawable.bg_shutter_photo);
                activeRecording = null;
                recordingStopping = false;
                applyVideoTorch();
                if (!finalizeEvent.hasError()) {
                    lastSavedUri = finalizeEvent.getOutputResults().getOutputUri();
                    galleryButton.setImageURI(lastSavedUri);
                    showTransientStatus("Đã lưu video");
                } else {
                    showStatus("Quay video lỗi: " + finalizeEvent.getError());
                }
                scheduleVideoRotationRefresh();
            }
        });
    }

    private void focusAt(float x, float y) {
        if (!isVideoMode && photoCameraDevice != null && photoCaptureSession != null) {
            try {
                CaptureRequest.Builder request = photoCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                request.addTarget(photoPreviewSurface);
                request.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
                request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
                photoCaptureSession.capture(request.build(), null, photoCameraHandler);
            } catch (Exception ignored) {
            }
            showFocusRing(x, y);
            return;
        }
        if (!isVideoMode && photoCamera != null) {
            try {
                photoCamera.autoFocus(null);
            } catch (Exception ignored) {
            }
            showFocusRing(x, y);
            return;
        }
        if (camera == null) return;
        MeteringPoint point = previewView.getMeteringPointFactory().createPoint(x, y);
        FocusMeteringAction action = new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF | FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, TimeUnit.SECONDS)
                .build();
        CameraControl control = camera.getCameraControl();
        control.startFocusAndMetering(action);
        showFocusRing(x, y);
    }

    private void showFocusRing(float x, float y) {
        focusRing.setX(x - focusRing.getWidth() / 2f);
        focusRing.setY(y - focusRing.getHeight() / 2f);
        focusRing.animate().cancel();
        focusRing.setScaleX(1.3f);
        focusRing.setScaleY(1.3f);
        focusRing.setAlpha(1f);
        focusRing.animate().scaleX(1f).scaleY(1f).alpha(0f).setDuration(650).start();
    }

    private void setVideoMode(boolean enabled) {
        if (activeRecording != null) return;
        isVideoMode = enabled;
        shutterButton.setContentDescription(enabled ? "Quay video" : "Chụp ảnh");
        updateModeTabs();
        startCamera();
    }

    private void updateModeTabs() {
        int accent = ContextCompat.getColor(this, R.color.brand_accent);
        photoMode.setBackground(makePill(!isVideoMode ? accent : Color.TRANSPARENT));
        videoMode.setBackground(makePill(isVideoMode ? accent : Color.TRANSPARENT));
        photoMode.setTextColor(!isVideoMode ? Color.BLACK : Color.WHITE);
        videoMode.setTextColor(isVideoMode ? Color.BLACK : Color.WHITE);
        watermarkOverlay.setVisibility(View.VISIBLE);
        updateBeautyUi();
    }

    private void updateBeautyUi() {
        boolean showBeauty = !usingBackCamera && !isVideoMode;
        beautyPanel.setVisibility(showBeauty ? View.VISIBLE : View.GONE);
        int accent = ContextCompat.getColor(this, R.color.brand_accent);
        smoothButton.setBackground(makePill(smoothSkinEnabled ? accent : Color.TRANSPARENT));
        brightButton.setBackground(makePill(brightenSkinEnabled ? accent : Color.TRANSPARENT));
        smoothButton.setTextColor(smoothSkinEnabled ? Color.BLACK : Color.WHITE);
        brightButton.setTextColor(brightenSkinEnabled ? Color.BLACK : Color.WHITE);
    }

    private void updateWatermarkText(Date date) {
        Locale vi = new Locale("vi", "VN");
        watermarkTime.setText(new SimpleDateFormat("HH:mm", vi).format(date));
        watermarkDate.setText(new SimpleDateFormat("dd/MM/yyyy", vi).format(date));
        watermarkWeekday.setText(capitalizeWords(new SimpleDateFormat("EEEE", vi).format(date)));
    }

    private OverlayEffect createVideoWatermarkEffect() {
        if (videoEffectThread == null) {
            videoEffectThread = new HandlerThread("VideoWatermark");
            videoEffectThread.start();
        }
        Handler handler = new Handler(videoEffectThread.getLooper());
        OverlayEffect effect = new OverlayEffect(
                CameraEffect.VIDEO_CAPTURE,
                0,
                handler,
                throwable -> runOnUiThread(() -> showTransientStatus("Watermark video lỗi"))
        );
        final long[] lastMinute = {-1};
        final int[] lastGeometry = {-1};
        effect.setOnDrawListener(frame -> {
            long minute = System.currentTimeMillis() / 60000;
            int geometry = java.util.Objects.hash(frame.getCropRect(), frame.getRotationDegrees());
            if (lastMinute[0] == minute && lastGeometry[0] == geometry) return true;
            Canvas canvas = frame.getOverlayCanvas();
            if (canvas == null) return false;
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR);
            drawVideoWatermarkForFrame(canvas, frame);
            lastMinute[0] = minute;
            lastGeometry[0] = geometry;
            return true;
        });
        return effect;
    }

    private void drawVideoWatermarkForFrame(Canvas canvas, androidx.camera.effects.Frame frame) {
        Rect crop = frame.getCropRect();
        int width = crop.width();
        int height = crop.height();
        int rotation = ((frame.getRotationDegrees() % 360) + 360) % 360;
        canvas.save();
        canvas.clipRect(crop);
        if (rotation == 90) {
            canvas.translate(crop.left, crop.bottom);
            canvas.rotate(-90);
            drawVideoWatermark(canvas, height, width, new Date());
        } else if (rotation == 270) {
            canvas.translate(crop.right, crop.top);
            canvas.rotate(90);
            drawVideoWatermark(canvas, height, width, new Date());
        } else if (rotation == 180) {
            canvas.translate(crop.right, crop.bottom);
            canvas.rotate(180);
            drawVideoWatermark(canvas, width, height, new Date());
        } else {
            canvas.translate(crop.left, crop.top);
            drawVideoWatermark(canvas, width, height, new Date());
        }
        canvas.restore();
    }

    private void drawVideoWatermark(Canvas canvas, int width, int height, Date date) {
        float scale = width / 1080f * 0.9f;
        float marginLeft = 44f * scale;
        float marginBottom = 60f * scale;
        float logoHeight = 120f * scale;
        float logoGap = 10f * scale;
        float timeSize = 100f * scale;
        float smallSize = 42f * scale;
        float barWidth = 6f * scale;
        float gapAfterTime = 22f * scale;
        float gapBeforeDate = 22f * scale;
        float rowGap = 48f * scale;

        Paint timePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        timePaint.setColor(Color.WHITE);
        timePaint.setTextSize(timeSize);
        timePaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        timePaint.setShadowLayer(7f * scale, 2f * scale, 2f * scale, Color.BLACK);

        Paint smallPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        smallPaint.setColor(Color.WHITE);
        smallPaint.setTextSize(smallSize);
        smallPaint.setShadowLayer(5f * scale, 2f * scale, 2f * scale, Color.BLACK);

        Locale vi = new Locale("vi", "VN");
        String time = new SimpleDateFormat("HH:mm", vi).format(date);
        String dateText = new SimpleDateFormat("dd/MM/yyyy", vi).format(date);
        String weekday = capitalizeWords(new SimpleDateFormat("EEEE", vi).format(date));

        Paint.FontMetrics timeMetrics = timePaint.getFontMetrics();
        Paint.FontMetrics smallMetrics = smallPaint.getFontMetrics();
        float smallLineHeight = smallMetrics.descent - smallMetrics.ascent;
        float textBlockHeight = smallLineHeight + rowGap;
        float textBaselineTop = height - marginBottom - rowGap;
        float textBaselineBottom = height - marginBottom;
        float timeBaseline = height - marginBottom - textBlockHeight / 2f - (timeMetrics.ascent + timeMetrics.descent) / 2f;
        float timeWidth = timePaint.measureText(time);
        float barLeft = marginLeft + timeWidth + gapAfterTime;
        float barTop = textBaselineTop + smallMetrics.ascent;
        float barBottom = textBaselineBottom + smallMetrics.descent;
        float infoLeft = barLeft + barWidth + gapBeforeDate;

        Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_watermark);
        if (logo != null) {
            Paint logoPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            float logoWidth = logo.getWidth() * (logoHeight / logo.getHeight());
            float logoTop = barTop - logoGap - logoHeight;
            canvas.drawBitmap(logo, null, new RectF(marginLeft, logoTop, marginLeft + logoWidth, logoTop + logoHeight), logoPaint);
            drawWatermarkLocation(canvas, marginLeft + logoWidth + 20f * scale, logoTop, logoHeight, scale);
            logo.recycle();
        }

        canvas.drawText(time, marginLeft, timeBaseline, timePaint);
        Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        barPaint.setColor(Color.parseColor("#FF7A00"));
        canvas.drawRect(barLeft, barTop, barLeft + barWidth, barBottom, barPaint);
        canvas.drawText(dateText, infoLeft, textBaselineTop, smallPaint);
        canvas.drawText(weekday, infoLeft, textBaselineBottom, smallPaint);
    }

    private void drawWatermarkLocation(Canvas canvas, float left, float top, float height, float scale) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        paint.setColor(Color.WHITE);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(52f * scale);
        paint.setShadowLayer(5f * scale, scale, scale, Color.BLACK);
        Paint.FontMetrics metrics = paint.getFontMetrics();
        canvas.drawText(getString(R.string.watermark_location), left,
                top + height / 2f - (metrics.ascent + metrics.descent) / 2f, paint);
    }

    private void closeVideoOverlayEffect() {
        if (videoOverlayEffect == null) return;
        videoOverlayEffect.close();
        videoOverlayEffect = null;
    }

    private String capitalizeWords(String value) {
        if (value == null || value.isEmpty()) return "";
        String[] words = value.split("\\s+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (builder.length() > 0) builder.append(' ');
            builder.append(word.substring(0, 1).toUpperCase(new Locale("vi", "VN")));
            if (word.length() > 1) builder.append(word.substring(1));
        }
        return builder.toString();
    }

    private GradientDrawable makePill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(16));
        return drawable;
    }

    private void cycleFlashMode() {
        if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            flashMode = ImageCapture.FLASH_MODE_ON;
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashMode = ImageCapture.FLASH_MODE_OFF;
        } else {
            flashMode = ImageCapture.FLASH_MODE_AUTO;
        }
        if (imageCapture != null) {
            imageCapture.setFlashMode(flashMode);
        }
        if (!isVideoMode && photoCamera != null) {
            applyLegacyFlashMode();
        }
        if (!isVideoMode && photoCameraDevice != null && photoCaptureSession != null) {
            restartCamera2Preview();
        }
        if (isVideoMode) {
            applyVideoTorch();
        }
        updateFlashButton();
    }

    private void updateFlashButton() {
        if (flashMode == ImageCapture.FLASH_MODE_AUTO) {
            flashButton.setText("A");
        } else if (flashMode == ImageCapture.FLASH_MODE_ON) {
            flashButton.setText("On");
        } else {
            flashButton.setText("Off");
        }
    }

    private void openLastCapture() {
        openGalleryApp();
    }

    private void openGalleryApp() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_APP_GALLERY);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Intent fallback = new Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            if (fallback.resolveActivity(getPackageManager()) != null) {
                startActivity(fallback);
            } else {
                Toast.makeText(this, "Chưa tìm thấy thư viện ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void loadLatestThumbnail() {
        Uri latestImage = queryLatest(MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        Uri latestVideo = queryLatest(MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        if (latestImage == null && latestVideo == null) return;
        lastSavedUri = latestVideo != null && isNewer(latestVideo, latestImage) ? latestVideo : latestImage;
        galleryButton.setImageURI(lastSavedUri);
    }

    private Uri queryLatest(Uri collection) {
        try (Cursor cursor = getContentResolver().query(
                collection,
                new String[]{MediaStore.MediaColumns._ID},
                null,
                null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return Uri.withAppendedPath(collection, String.valueOf(id));
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return null;
    }

    private boolean isNewer(Uri candidate, Uri reference) {
        if (candidate == null) return false;
        if (reference == null) return true;
        return readDateAdded(candidate) >= readDateAdded(reference);
    }

    private long readDateAdded(Uri uri) {
        try (Cursor cursor = getContentResolver().query(
                uri,
                new String[]{MediaStore.MediaColumns.DATE_ADDED},
                null,
                null,
                null
        )) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (SecurityException ignored) {
            return 0;
        }
        return 0;
    }

    private boolean hasAllPermissions() {
        for (String permission : requiredPermissions()) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private String[] requiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
            };
        }
        return new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
        };
    }

    private Size findLargestJpegSize(int lensFacing) {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            Size best = null;
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                int targetFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                        ? CameraCharacteristics.LENS_FACING_FRONT
                        : CameraCharacteristics.LENS_FACING_BACK;
                if (facing == null || facing != targetFacing) continue;

                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) continue;
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                if (sizes == null || sizes.length == 0) continue;
                for (Size size : sizes) {
                    if (best == null || area(size) > area(best)) {
                        best = size;
                    }
                }
            }
            return best;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String findCamera2Id(int lensFacing) throws Exception {
        List<String> candidates = findCamera2Candidates(lensFacing);
        if (!candidates.isEmpty()) return candidates.get(0);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        return manager.getCameraIdList()[0];
    }

    private List<String> findCamera2Candidates(int lensFacing) throws Exception {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        int targetFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                ? CameraCharacteristics.LENS_FACING_FRONT
                : CameraCharacteristics.LENS_FACING_BACK;
        List<CameraChoice> choices = new ArrayList<>();
        for (String cameraId : manager.getCameraIdList()) {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing == null || facing != targetFacing) continue;
            Size size = largestJpegSizeForCamera(characteristics);
            choices.add(new CameraChoice(cameraId, size == null ? 0 : area(size), maxFocalLength(characteristics)));
        }
        choices.sort((a, b) -> {
            if (targetFacing == CameraCharacteristics.LENS_FACING_BACK) {
                if ("0".equals(a.id) && !"0".equals(b.id)) return -1;
                if (!"0".equals(a.id) && "0".equals(b.id)) return 1;
            }
            int pixelCompare = Long.compare(b.pixels, a.pixels);
            if (pixelCompare != 0) return pixelCompare;
            return Float.compare(b.focalLength, a.focalLength);
        });
        List<String> result = new ArrayList<>();
        for (CameraChoice choice : choices) result.add(choice.id);
        return result;
    }

    private static class CameraChoice {
        final String id;
        final long pixels;
        final float focalLength;

        CameraChoice(String id, long pixels, float focalLength) {
            this.id = id;
            this.pixels = pixels;
            this.focalLength = focalLength;
        }
    }

    private Size findLargestJpegSizeForId(String cameraId) {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            return largestJpegSizeForCamera(manager.getCameraCharacteristics(cameraId));
        } catch (Exception exception) {
            return null;
        }
    }

    private void applyStillQuality(CaptureRequest.Builder request) throws Exception {
        CameraCharacteristics c = ((CameraManager) getSystemService(Context.CAMERA_SERVICE))
                .getCameraCharacteristics(photoCamera2Id);
        setSupportedMode(request, CaptureRequest.EDGE_MODE, c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES),
                CaptureRequest.EDGE_MODE_HIGH_QUALITY);
        setSupportedMode(request, CaptureRequest.NOISE_REDUCTION_MODE,
                c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES),
                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
        setSupportedMode(request, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION),
                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
    }

    private void setSupportedMode(CaptureRequest.Builder request, CaptureRequest.Key<Integer> key,
                                  int[] supported, int mode) {
        if (supported == null) return;
        for (int available : supported) {
            if (available == mode) { request.set(key, mode); return; }
        }
    }

    private Size largestJpegSizeForCamera(CameraCharacteristics characteristics) {
        StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map == null) return null;
        Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
        if (sizes == null || sizes.length == 0) return null;
        List<Size> all = new ArrayList<>(Arrays.asList(sizes));
        Size[] high = map.getHighResolutionOutputSizes(ImageFormat.JPEG);
        if (high != null) all.addAll(Arrays.asList(high));
        return all.stream()
                .max(Comparator.comparingLong(this::area))
                .orElse(null);
    }

    private float maxFocalLength(CameraCharacteristics characteristics) {
        float[] focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
        if (focalLengths == null || focalLengths.length == 0) return 0f;
        float best = focalLengths[0];
        for (float focalLength : focalLengths) {
            best = Math.max(best, focalLength);
        }
        return best;
    }

    private Size bestCamera2PreviewSize(String cameraId) {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            StreamConfigurationMap map = manager.getCameraCharacteristics(cameraId)
                    .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map == null) return null;
            Size[] sizes = map.getOutputSizes(SurfaceTexture.class);
            if (sizes == null || sizes.length == 0) return null;
            return Arrays.stream(sizes)
                    .filter(size -> size.getWidth() <= 1920 && size.getHeight() <= 1080)
                    .max(Comparator.comparingLong(size -> (long) size.getWidth() * size.getHeight()))
                    .orElse(sizes[0]);
        } catch (Exception exception) {
            return null;
        }
    }

    private int camera2JpegOrientation() {
        try {
            CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(photoCamera2Id);
            Integer sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
            int sensor = sensorOrientation == null ? 90 : sensorOrientation;
            if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                return (sensor + degrees) % 360;
            }
            return (sensor - degrees + 360) % 360;
        } catch (Exception exception) {
            return 90;
        }
    }

    private int findLegacyCameraId(boolean back) {
        int desired = back ? android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK : android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        int bestId = 0;
        long bestPixels = -1;
        for (int i = 0; i < android.hardware.Camera.getNumberOfCameras(); i++) {
            android.hardware.Camera.getCameraInfo(i, info);
            if (info.facing != desired) continue;
            android.hardware.Camera probe = null;
            try {
                probe = android.hardware.Camera.open(i);
                android.hardware.Camera.Size size = largestLegacySize(probe.getParameters().getSupportedPictureSizes());
                long pixels = size == null ? 0 : (long) size.width * size.height;
                if (pixels > bestPixels) {
                    bestPixels = pixels;
                    bestId = i;
                }
            } catch (Exception ignored) {
            } finally {
                if (probe != null) probe.release();
            }
        }
        return bestId;
    }

    private Size findLargestLegacyPhotoSize(boolean back) {
        android.hardware.Camera probe = null;
        try {
            int cameraId = findLegacyCameraId(back);
            probe = android.hardware.Camera.open(cameraId);
            android.hardware.Camera.Size size = largestLegacySize(probe.getParameters().getSupportedPictureSizes());
            if (size != null) return new Size(size.width, size.height);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (probe != null) probe.release();
        }
        return null;
    }

    private long area(Size size) {
        return (long) size.getWidth() * size.getHeight();
    }

    private android.hardware.Camera.Size largestLegacySize(java.util.List<android.hardware.Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        return sizes.stream().max(Comparator.comparingLong(size -> (long) size.width * size.height)).orElse(sizes.get(0));
    }

    private android.hardware.Camera.Size bestLegacyPreviewSize(java.util.List<android.hardware.Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        return sizes.stream()
                .filter(size -> size.width <= 1920 && size.height <= 1080)
                .max(Comparator.comparingLong(size -> (long) size.width * size.height))
                .orElse(sizes.get(0));
    }

    private void applyLegacyFlashMode() {
        try {
            android.hardware.Camera.Parameters params = photoCamera.getParameters();
            java.util.List<String> modes = params.getSupportedFlashModes();
            if (modes == null) return;
            String mode = android.hardware.Camera.Parameters.FLASH_MODE_AUTO;
            if (flashMode == ImageCapture.FLASH_MODE_ON) mode = android.hardware.Camera.Parameters.FLASH_MODE_ON;
            if (flashMode == ImageCapture.FLASH_MODE_OFF) mode = android.hardware.Camera.Parameters.FLASH_MODE_OFF;
            if (modes.contains(mode)) {
                params.setFlashMode(mode);
                photoCamera.setParameters(params);
            }
        } catch (Exception ignored) {
        }
    }

    private int displayOrientation(int cameraId) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private int photoRotation() {
        if (photoCameraId < 0) return 90;
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(photoCameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = rotation == Surface.ROTATION_90 ? 90 : rotation == Surface.ROTATION_180 ? 180 : rotation == Surface.ROTATION_270 ? 270 : 0;
        if (info.facing == android.hardware.Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (info.orientation + degrees) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private void releasePhotoCamera() {
        if (photoCamera == null) return;
        try {
            photoCamera.stopPreview();
        } catch (Exception ignored) {
        }
        photoCamera.release();
        photoCamera = null;
        photoCameraId = -1;
    }

    private void startPhotoThread() {
        if (photoCameraThread != null) return;
        photoCameraThread = new HandlerThread("PhotoCamera2");
        photoCameraThread.start();
        photoCameraHandler = new Handler(photoCameraThread.getLooper());
    }

    private void releasePhotoCamera2() {
        pendingPhoto = null;
        if (photoCaptureSession != null) {
            photoCaptureSession.close();
            photoCaptureSession = null;
        }
        if (photoCameraDevice != null) {
            photoCameraDevice.close();
            photoCameraDevice = null;
        }
        if (photoImageReader != null) {
            photoImageReader.close();
            photoImageReader = null;
        }
        if (photoPreviewSurface != null) {
            photoPreviewSurface.release();
            photoPreviewSurface = null;
        }
        if (photoCameraThread != null) {
            photoCameraThread.quitSafely();
            photoCameraThread = null;
            photoCameraHandler = null;
        }
        photoCamera2Id = null;
    }

    private void runScreenFlash(Runnable capture) {
        screenFlashView.animate().cancel();
        screenFlashView.setVisibility(View.VISIBLE);
        screenFlashView.setAlpha(0f);
        screenFlashView.animate()
                .alpha(1f)
                .setDuration(80)
                .withEndAction(() -> {
                    capture.run();
                    screenFlashView.animate()
                            .alpha(0f)
                            .setStartDelay(260)
                            .setDuration(180)
                            .withEndAction(() -> screenFlashView.setVisibility(View.GONE))
                            .start();
                })
                .start();
    }

    private void showTransientStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
        statusText.removeCallbacks(hideStatusRunnable);
        statusText.postDelayed(hideStatusRunnable, 1600);
    }

    private void showStatus(String message) {
        statusText.setText(message);
        statusText.setVisibility(View.VISIBLE);
    }

    private void hideStatus() {
        statusText.setVisibility(View.GONE);
    }

    private String timestamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis());
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemChrome() {
        Window window = getWindow();
        window.setNavigationBarColor(Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(true);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityResumed = true;
        if (!hasAllPermissions() || shutterButton == null || restartingFromResume) return;
        if (skipNextResumeStart) {
            skipNextResumeStart = false;
            return;
        }
        restartingFromResume = true;
        timerHandler.postDelayed(() -> {
            restartingFromResume = false;
            loadLatestThumbnail();
            if (activeRecording == null) {
                startCamera();
            }
        }, 250);
    }

    @Override
    protected void onPause() {
        activityResumed = false;
        cameraGeneration++;
        timerHandler.removeCallbacks(refreshVideoRotation);
        if (activeRecording == null) {
            releasePhotoCamera();
            releasePhotoCamera2();
        }
        if (camera != null) {
            camera.getCameraControl().enableTorch(false);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        timerHandler.removeCallbacks(timerTick);
        timerHandler.removeCallbacks(watermarkTick);
        timerHandler.removeCallbacks(refreshVideoRotation);
        if (activeRecording != null) {
            activeRecording.stop();
            activeRecording = null;
        }
        releasePhotoCamera();
        releasePhotoCamera2();
        closeVideoOverlayEffect();
        cameraExecutor.shutdown();
    }
}
