package com.leeduc.watermarkcam

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.animation.AlphaAnimation
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.effects.OverlayEffect
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var btnCapture: ImageButton
    private lateinit var btnFlash: ImageButton
    private lateinit var btnSwitchCamera: ImageButton
    private lateinit var txtExposure: TextView
    private lateinit var focusRing: android.view.View
    private lateinit var screenFlashOverlay: android.view.View
    private lateinit var reviewOverlay: android.view.View
    private lateinit var pagerReview: ViewPager2
    private lateinit var btnCloseReview: ImageButton
    private lateinit var btnDeleteReview: ImageButton
    private var reviewAdapter: ReviewPagerAdapter? = null

    // Live clock overlay shown on the camera preview, styled the same as the
    // watermark that gets baked into the saved photo (see drawTimestampWatermark).
    private lateinit var txtLiveTime: TextView
    private lateinit var txtLiveDate: TextView
    private lateinit var txtLiveDay: TextView
    private lateinit var imgLiveLogo: ImageView
    private val clockHandler = Handler(Looper.getMainLooper())
    private val clockTickRunnable = object : Runnable {
        override fun run() {
            updateLiveClock()
            clockHandler.postDelayed(this, 1000L)
        }
    }

    // --- Video recording ---
    private lateinit var modeSwitchContainer: LinearLayout
    private lateinit var txtModePhoto: TextView
    private lateinit var txtModeVideo: TextView
    private lateinit var txtRecTimer: TextView
    private var isVideoMode = false
    private var isRecordingVideo = false
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    // Burns the timestamp watermark directly into the camera pipeline (preview + video)
    // using OpenGL, frame by frame — same layout as drawTimestampOverlay() used for photos.
    private var overlayEffect: OverlayEffect? = null
    private lateinit var effectHandlerThread: HandlerThread
    private lateinit var effectHandler: Handler

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var cameraExecutor: ExecutorService

    // true = back camera, false = front camera
    private var usingBackCamera = true

    // Remembers the flash on/off state between app launches.
    // Back camera: fires the real hardware flash. Front camera: flashes the screen white instead.
    private val prefs by lazy { getSharedPreferences("watermarkcam_prefs", MODE_PRIVATE) }
    private var flashOn: Boolean
        get() = prefs.getBoolean(KEY_FLASH, false)
        set(value) = prefs.edit().putBoolean(KEY_FLASH, value).apply()

    // manual exposure drag state
    private var touchDownX = 0f
    private var touchDownY = 0f
    private var startExposureIndex = 0
    private var isDraggingExposure = false

    private val requestPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.CAMERA] == true) {
                startCamera()
            } else {
                Toast.makeText(this, "Cần quyền Camera để sử dụng ứng dụng", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewFinder = findViewById(R.id.viewFinder)
        btnCapture = findViewById(R.id.btnCapture)
        btnFlash = findViewById(R.id.btnFlash)
        btnSwitchCamera = findViewById(R.id.btnSwitchCamera)
        txtExposure = findViewById(R.id.txtExposure)
        focusRing = findViewById(R.id.focusRing)
        screenFlashOverlay = findViewById(R.id.screenFlashOverlay)
        reviewOverlay = findViewById(R.id.reviewOverlay)
        pagerReview = findViewById(R.id.pagerReview)
        btnCloseReview = findViewById(R.id.btnCloseReview)
        btnDeleteReview = findViewById(R.id.btnDeleteReview)
        txtLiveTime = findViewById(R.id.txtLiveTime)
        txtLiveDate = findViewById(R.id.txtLiveDate)
        txtLiveDay = findViewById(R.id.txtLiveDay)
        imgLiveLogo = findViewById(R.id.imgLiveLogo)
        modeSwitchContainer = findViewById(R.id.modeSwitchContainer)
        txtModePhoto = findViewById(R.id.txtModePhoto)
        txtModeVideo = findViewById(R.id.txtModeVideo)
        txtRecTimer = findViewById(R.id.txtRecTimer)

        cameraExecutor = Executors.newSingleThreadExecutor()
        effectHandlerThread = HandlerThread("WatermarkOverlayEffect").apply { start() }
        effectHandler = Handler(effectHandlerThread.looper)
        updateFlashIcon()
        setupLiveLogo()

        btnFlash.setOnClickListener {
            flashOn = !flashOn
            imageCapture?.flashMode =
                if (usingBackCamera && flashOn) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
            updateFlashIcon()
            updateTorchState()
        }

        btnSwitchCamera.setOnClickListener {
            if (isRecordingVideo) {
                Toast.makeText(this, "Không thể đổi camera khi đang quay video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            usingBackCamera = !usingBackCamera
            bindCameraUseCases()
            updateFlashIcon()
        }

        btnCapture.setOnClickListener { if (isVideoMode) toggleRecording() else takePhoto() }
        btnCloseReview.setOnClickListener { closeReview() }
        btnDeleteReview.setOnClickListener { confirmDeleteCurrentPhoto() }
        txtModePhoto.setOnClickListener { setVideoMode(false) }
        txtModeVideo.setOnClickListener { setVideoMode(true) }

        setupTapToFocusAndExposure()

        val permissionsNeeded = mutableListOf(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
            if (permissionsNeeded.size > 1) requestPermissions.launch(permissionsNeeded.toTypedArray())
        } else {
            requestPermissions.launch(permissionsNeeded.toTypedArray())
        }
    }

    /** Switches between photo mode (default shutter) and video mode (red record button). */
    private fun setVideoMode(video: Boolean) {
        if (isRecordingVideo) return
        isVideoMode = video
        txtModePhoto.setBackgroundColor(if (video) android.graphics.Color.TRANSPARENT else android.graphics.Color.WHITE)
        txtModePhoto.setTextColor(if (video) android.graphics.Color.WHITE else android.graphics.Color.BLACK)
        txtModeVideo.setBackgroundColor(if (video) android.graphics.Color.WHITE else android.graphics.Color.TRANSPARENT)
        txtModeVideo.setTextColor(if (video) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        btnCapture.contentDescription = if (video) "Quay video" else "Chụp ảnh"
        bindCameraUseCases()
    }

    private fun updateFlashIcon() {
        btnFlash.setImageResource(if (flashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
    }

    /** Shows the same optional res/drawable/logo_watermark above the live clock, if present. */
    private fun setupLiveLogo() {
        val logoResId = resources.getIdentifier("logo_watermark", "drawable", packageName)
        if (logoResId != 0) {
            imgLiveLogo.setImageResource(logoResId)
            imgLiveLogo.visibility = android.view.View.VISIBLE
        } else {
            imgLiveLogo.visibility = android.view.View.GONE
        }
    }

    /** Refreshes the live clock overlay text, using the exact same formats as drawTimestampWatermark(). */
    private fun updateLiveClock() {
        val now = Date()
        txtLiveTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        txtLiveDate.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
        txtLiveDay.text = SimpleDateFormat("EEEE", Locale("vi")).format(now)
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    override fun onResume() {
        super.onResume()
        clockHandler.post(clockTickRunnable)
    }

    override fun onPause() {
        super.onPause()
        clockHandler.removeCallbacks(clockTickRunnable)
        if (isRecordingVideo) stopRecording()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        if (!::cameraProvider.isInitialized) return

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(viewFinder.surfaceProvider)
        }

        val cameraSelector = if (usingBackCamera)
            CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

        val groupBuilder = UseCaseGroup.Builder().addUseCase(preview)

        // Photo and video are bound to SEPARATE use-case combinations (never Preview +
        // ImageCapture + VideoCapture all at once). Most phones are a "LIMITED" camera
        // device: binding 3 concurrent streams forces the whole pipeline down to a small
        // shared resolution/fps. Splitting like this is what restores the sensor's real
        // ~13MP for photos and gives video a proper resolution/frame rate.
        if (isVideoMode) {
            imageCapture = null

            val recorder = Recorder.Builder()
                .setQualitySelector(
                    QualitySelector.from(Quality.FHD, FallbackStrategy.higherQualityOrLowerThan(Quality.FHD))
                )
                .build()
            videoCapture = VideoCapture.withOutput(recorder)

            // Recreate the effect on every rebind so it isn't left bound to a stale
            // pipeline; the old one is closed first to release its GL resources.
            overlayEffect?.close()
            overlayEffect = OverlayEffect(
                OverlayEffect.VIDEO_CAPTURE,
                0,
                effectHandler
            ) { error -> error.printStackTrace() }.also { effect ->
                effect.setOnDrawListener { frame ->
                    drawTimestampOverlayOnFrame(frame)
                    true
                }
            }

            groupBuilder.addUseCase(videoCapture!!).addEffect(overlayEffect!!)
        } else {
            videoCapture = null
            overlayEffect?.close()
            overlayEffect = null

            // Ask CameraX for the sensor's highest available resolution instead of letting
            // it pick a default (which is often much lower than the sensor's real output).
            val highResSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(ResolutionStrategy.HIGHEST_AVAILABLE_STRATEGY)
                .build()

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setResolutionSelector(highResSelector)
                .setFlashMode(
                    if (usingBackCamera && flashOn) ImageCapture.FLASH_MODE_ON
                    else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            groupBuilder.addUseCase(imageCapture!!)
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, groupBuilder.build())
            updateTorchState()
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể khởi động camera: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * In video mode, turning "flash" on means the torch should stay lit continuously for
     * the whole recording (unlike photo mode, where ImageCapture fires the flash only for
     * an instant). Re-applied after every rebind since unbind/rebind resets torch state.
     */
    private fun updateTorchState() {
        val shouldTorch = usingBackCamera && flashOn && isVideoMode
        camera?.cameraControl?.enableTorch(shouldTorch)
    }

    /** Tap on the preview = focus + auto-exposure at that point. Tap-and-drag vertically = manual brightness (EV). */
    private fun setupTapToFocusAndExposure() {
        viewFinder.setOnTouchListener { _, event ->
            val cam = camera ?: return@setOnTouchListener true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    touchDownX = event.x
                    touchDownY = event.y
                    isDraggingExposure = false
                    startExposureIndex = cam.cameraInfo.exposureState.exposureCompensationIndex

                    focusOn(cam, event.x, event.y)
                    showFocusRing(event.x, event.y)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = touchDownY - event.y
                    if (!isDraggingExposure && abs(deltaY) > 30f) {
                        isDraggingExposure = true
                        txtExposure.visibility = android.view.View.VISIBLE
                    }
                    if (isDraggingExposure) {
                        adjustExposure(cam, deltaY)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    focusRing.postDelayed({ focusRing.visibility = android.view.View.INVISIBLE }, 400)
                    if (isDraggingExposure) {
                        txtExposure.postDelayed({ txtExposure.visibility = android.view.View.INVISIBLE }, 800)
                    }
                    isDraggingExposure = false
                    true
                }
                else -> false
            }
        }
    }

    private fun focusOn(cam: Camera, x: Float, y: Float) {
        val factory = viewFinder.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun adjustExposure(cam: Camera, deltaY: Float) {
        val info = cam.cameraInfo.exposureState
        if (!info.isExposureCompensationSupported) return
        val range = info.exposureCompensationRange
        // ~28px of drag per exposure step
        val stepsMoved = (deltaY / 28f).roundToInt()
        val target = (startExposureIndex + stepsMoved).coerceIn(range.lower, range.upper)
        cam.cameraControl.setExposureCompensationIndex(target)
        val ev = target * info.exposureCompensationStep.toFloat()
        txtExposure.text = String.format(Locale.getDefault(), "EV %.1f", ev)
    }

    private fun showFocusRing(x: Float, y: Float) {
        focusRing.translationX = x - focusRing.width / 2f
        focusRing.translationY = y - focusRing.height / 2f
        focusRing.visibility = android.view.View.VISIBLE
        focusRing.alpha = 1f
        focusRing.startAnimation(AlphaAnimation(1f, 1f).apply { duration = 0 })
    }

    private fun takePhoto() {
        if (!usingBackCamera && flashOn) {
            // Front camera "flash" = flash the screen white at full brightness, then shoot.
            showScreenFlash()
            viewFinder.postDelayed({ capturePhoto() }, 350)
        } else {
            capturePhoto()
        }
    }

    private fun showScreenFlash() {
        screenFlashOverlay.visibility = android.view.View.VISIBLE
        val lp = window.attributes
        lp.screenBrightness = 1f
        window.attributes = lp
    }

    private fun hideScreenFlash() {
        screenFlashOverlay.visibility = android.view.View.GONE
        val lp = window.attributes
        lp.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        btnCapture.isEnabled = false
        val isBack = usingBackCamera

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    var bitmap = imageProxyToBitmap(image, isBack)
                    image.close()
                    if (!isBack) {
                        // Front camera only: 30% skin-smoothing + 30% skin-brightening
                        bitmap = applyFrontCameraBeauty(bitmap)
                    }
                    val watermarked = drawTimestampWatermark(bitmap)
                    saveToGallery(watermarked)
                    runOnUiThread {
                        hideScreenFlash()
                        btnCapture.isEnabled = true
                        showReview()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        hideScreenFlash()
                        btnCapture.isEnabled = true
                        Toast.makeText(
                            this@MainActivity,
                            "Chụp ảnh thất bại: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun toggleRecording() {
        if (isRecordingVideo) stopRecording() else startRecording()
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        val videoCap = videoCapture ?: return
        val name = "VID_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/WatermarkCam")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = videoCap.output.prepareRecording(this, outputOptions)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            pending = pending.withAudioEnabled()
        }

        activeRecording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> onRecordingStarted()
                is VideoRecordEvent.Status -> {
                    val seconds = TimeUnit.NANOSECONDS.toSeconds(event.recordingStats.recordedDurationNanos)
                    updateRecTimer(seconds)
                }
                is VideoRecordEvent.Finalize -> {
                    onRecordingStopped()
                    if (event.hasError()) {
                        Toast.makeText(
                            this, "Quay video thất bại: ${event.cause?.message}", Toast.LENGTH_LONG
                        ).show()
                    } else {
                        Toast.makeText(this, "Đã lưu video vào Movies/WatermarkCam", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> {}
            }
        }
    }

    private fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    private fun onRecordingStarted() {
        isRecordingVideo = true
        btnCapture.setBackgroundResource(R.drawable.shutter_button_bg_recording)
        modeSwitchContainer.visibility = android.view.View.INVISIBLE
        txtRecTimer.visibility = android.view.View.VISIBLE
        updateRecTimer(0)
    }

    private fun onRecordingStopped() {
        isRecordingVideo = false
        btnCapture.setBackgroundResource(R.drawable.shutter_button_bg)
        modeSwitchContainer.visibility = android.view.View.VISIBLE
        txtRecTimer.visibility = android.view.View.GONE
    }

    private fun updateRecTimer(totalSeconds: Long) {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        txtRecTimer.text = String.format(Locale.getDefault(), "● %02d:%02d", m, s)
    }

    /**
     * Shows the review screen: newest photo first, swipe left/right for older ones,
     * pinch or double-tap to zoom. Reads the list fresh from the gallery every time,
     * so it always reflects what's actually saved (including after a delete).
     */
    private fun showReview() {
        val uris = queryWatermarkCamPhotos()
        if (uris.isEmpty()) return
        reviewAdapter = ReviewPagerAdapter(uris)
        pagerReview.adapter = reviewAdapter
        pagerReview.setCurrentItem(0, false)
        reviewOverlay.visibility = android.view.View.VISIBLE
    }

    private fun closeReview() {
        reviewOverlay.visibility = android.view.View.GONE
        pagerReview.adapter = null
        reviewAdapter = null
    }

    /** Queries every photo this app has saved to Pictures/WatermarkCam, newest first. */
    private fun queryWatermarkCamPhotos(): MutableList<Uri> {
        val result = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection: String
        val selectionArgs: Array<String>
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selection = "${MediaStore.Images.Media.RELATIVE_PATH} = ?"
            selectionArgs = arrayOf("Pictures/WatermarkCam/")
        } else {
            selection = "${MediaStore.Images.Media.DATA} LIKE ?"
            selectionArgs = arrayOf("%/WatermarkCam/%")
        }
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                result.add(
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idCol))
                )
            }
        }
        return result
    }

    /** Asks for confirmation, then permanently deletes the photo currently shown in the review pager. */
    private fun confirmDeleteCurrentPhoto() {
        val adapter = reviewAdapter ?: return
        val position = pagerReview.currentItem
        if (position !in 0 until adapter.itemCount) return

        AlertDialog.Builder(this)
            .setTitle("Xoá ảnh?")
            .setMessage("Ảnh sẽ bị xoá vĩnh viễn khỏi thư viện, không thể khôi phục.")
            .setPositiveButton("Xoá") { _, _ -> deletePhotoAt(position) }
            .setNegativeButton("Huỷ", null)
            .show()
    }

    private fun deletePhotoAt(position: Int) {
        val adapter = reviewAdapter ?: return
        val uri = adapter.uriAt(position) ?: return
        try {
            contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Không xoá được ảnh: ${e.message}", Toast.LENGTH_SHORT).show()
            return
        }
        adapter.removeAt(position)
        if (adapter.itemCount == 0) closeReview()
    }

    /**
     * Front-camera-only "beauty" pass: blends a softened (smoothed) version of the
     * photo at 30% opacity on top (skin smoothing), then blends a brightened/whitened
     * version at 30% opacity on top of that (skin brightening). Both are simple opacity
     * blends — like duplicating a layer in an editor, applying an effect, and setting
     * that layer's opacity to 30% — so skin looks softer and a bit brighter without
     * flattening the whole photo.
     */
    private fun applyFrontCameraBeauty(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val blendAlpha = 77 // 255 * 0.30 ≈ 30% opacity

        // --- Pass 1: skin smoothing (30% blend of a soft-blurred copy) ---
        val blurred = cheapBlur(source)
        val smoothed = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(smoothed).apply {
            drawBitmap(source, 0f, 0f, null)
            drawBitmap(blurred, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                alpha = blendAlpha
            })
        }

        // --- Pass 2: skin brightening/whitening (30% blend of a brightened copy) ---
        val whitenMatrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, 26f,
                0f, 1f, 0f, 0f, 26f,
                0f, 0f, 1f, 0f, 26f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawBitmap(smoothed, 0f, 0f, null)
            drawBitmap(smoothed, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = blendAlpha
                colorFilter = ColorMatrixColorFilter(whitenMatrix)
            })
        }
        return result
    }

    /** Fast approximate blur: shrink, box-blur the small copy, then scale back up. */
    private fun cheapBlur(source: Bitmap): Bitmap {
        val downscale = 6
        val smallW = (source.width / downscale).coerceAtLeast(2)
        val smallH = (source.height / downscale).coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)

        val radius = 4
        var pixels = IntArray(smallW * smallH)
        small.getPixels(pixels, 0, smallW, 0, 0, smallW, smallH)
        pixels = boxBlurPass(pixels, smallW, smallH, radius, horizontal = true)
        pixels = boxBlurPass(pixels, smallW, smallH, radius, horizontal = false)
        val blurredSmall = Bitmap.createBitmap(smallW, smallH, Bitmap.Config.ARGB_8888)
        blurredSmall.setPixels(pixels, 0, smallW, 0, 0, smallW, smallH)

        return Bitmap.createScaledBitmap(blurredSmall, source.width, source.height, true)
    }

    private fun boxBlurPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean): IntArray {
        val result = IntArray(pixels.size)
        if (horizontal) {
            for (y in 0 until h) {
                val rowStart = y * w
                for (x in 0 until w) {
                    var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                    val xs = (x - radius).coerceAtLeast(0)
                    val xe = (x + radius).coerceAtMost(w - 1)
                    for (xx in xs..xe) {
                        val p = pixels[rowStart + xx]
                        a += (p shr 24) and 0xFF; r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF; b += p and 0xFF
                        count++
                    }
                    result[rowStart + x] =
                        ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        } else {
            for (x in 0 until w) {
                for (y in 0 until h) {
                    var a = 0; var r = 0; var g = 0; var b = 0; var count = 0
                    val ys = (y - radius).coerceAtLeast(0)
                    val ye = (y + radius).coerceAtMost(h - 1)
                    for (yy in ys..ye) {
                        val p = pixels[yy * w + x]
                        a += (p shr 24) and 0xFF; r += (p shr 16) and 0xFF
                        g += (p shr 8) and 0xFF; b += p and 0xFF
                        count++
                    }
                    result[y * w + x] =
                        ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                }
            }
        }
        return result
    }

    /** Converts the captured ImageProxy (JPEG) into a correctly-rotated Bitmap. */
    private fun imageProxyToBitmap(image: ImageProxy, isBackCamera: Boolean): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        var bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    /** Bakes the same watermark drawn live on video into the final saved photo bitmap. */
    private fun drawTimestampWatermark(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        drawTimestampOverlay(canvas, result.width.toFloat(), result.height.toFloat())
        return result
    }

    /**
     * Draws the overlay onto a video frame from the OverlayEffect, first rotating/mirroring
     * the canvas so its coordinate space matches the final, upright output — then delegates
     * to the exact same drawTimestampOverlay() used for photos, so video and photo watermarks
     * look identical.
     */
    private fun drawTimestampOverlayOnFrame(frame: androidx.camera.effects.Frame) {
        val canvas = frame.overlayCanvas
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.save()
        val w = frame.size.width.toFloat()
        val h = frame.size.height.toFloat()
        canvas.translate(w / 2f, h / 2f)
        canvas.rotate(-frame.rotationDegrees.toFloat())
        if (frame.isMirroring) canvas.scale(-1f, 1f)
        val rotated90 = frame.rotationDegrees % 180 != 0
        val drawW = if (rotated90) h else w
        val drawH = if (rotated90) w else h
        canvas.translate(-drawW / 2f, -drawH / 2f)
        drawTimestampOverlay(canvas, drawW, drawH)
        canvas.restore()
    }

    /**
     * Draws a bottom-left timestamp watermark: optional logo on top, then a large time
     * next to a thin orange divider bar, with the date and weekday to the right of it —
     * matching the layout of a typical verified-timestamp camera app.
     * Drop a drawable named "logo_watermark" (e.g. your own company logo PNG) into
     * res/drawable to have it appear automatically; otherwise no logo is drawn.
     * Shared by photo saving (Canvas over a Bitmap) and live video recording
     * (Canvas over an OverlayEffect frame) so both look identical.
     */
    private fun drawTimestampOverlay(canvas: Canvas, w: Float, h: Float) {
        val scale = w / 1080f

        val now = Date()
        val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
        val dateText = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(now)
        val dayText = SimpleDateFormat("EEEE", Locale("vi")).format(now)
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 100f * scale
            typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
            setShadowLayer(7f * scale, 2f * scale, 2f * scale, Color.BLACK)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 42f * scale
            typeface = Typeface.DEFAULT
            setShadowLayer(5f * scale, 2f * scale, 2f * scale, Color.BLACK)
        }
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF7A00")
        }

        val marginLeft = 44f * scale
        val marginBottom = 60f * scale
        val lineGap = 48f * scale

        val dayBaseline = h - marginBottom
        val dateBaseline = dayBaseline - lineGap
        val dayMetrics = smallPaint.fontMetrics

        // The 2-line block (date on top, weekday below) — its visual top and bottom edges.
        val blockTop = dateBaseline + dayMetrics.ascent
        val blockBottom = dayBaseline + dayMetrics.descent
        val blockCenter = (blockTop + blockBottom) / 2f

        // Vertically center the big time text against that whole 2-line block,
        // so its top lines up near the date line and its bottom near the weekday line.
        val timeMetrics = timePaint.fontMetrics
        val timeVisualCenterOffset = (timeMetrics.ascent + timeMetrics.descent) / 2f
        val timeBaseline = blockCenter - timeVisualCenterOffset
        val timeTop = timeBaseline + timeMetrics.ascent
        val timeBottom = timeBaseline + timeMetrics.descent

        val barTop = minOf(blockTop, timeTop)
        val barBottom = maxOf(blockBottom, timeBottom)

        // Optional logo above the timestamp block — only drawn if the app provides
        // res/drawable/logo_watermark (your own logo). Sits directly above the tallest
        // element, so it never overlaps the time/date/weekday text.
        val logoResId = resources.getIdentifier("logo_watermark", "drawable", packageName)
        if (logoResId != 0) {
            val logo = BitmapFactory.decodeResource(resources, logoResId)
            if (logo != null) {
                val targetH = 130f * scale
                val targetW = targetH * (logo.width.toFloat() / logo.height.toFloat())
                val gapAboveText = 10f * scale
                val logoBottom = barTop - gapAboveText
                canvas.drawBitmap(
                    logo, null,
                    RectF(marginLeft, logoBottom - targetH, marginLeft + targetW, logoBottom),
                    null
                )
            }
        }

        canvas.drawText(timeText, marginLeft, timeBaseline, timePaint)

        val barLeft = marginLeft + timePaint.measureText(timeText) + 22f * scale
        canvas.drawRect(
            barLeft, barTop,
            barLeft + 6f * scale, barBottom,
            barPaint
        )

        val textLeft2 = barLeft + 22f * scale
        canvas.drawText(dateText, textLeft2, dateBaseline, smallPaint)
        canvas.drawText(dayText, textLeft2, dayBaseline, smallPaint)
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val filename = "IMG_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WatermarkCam")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val resolver = contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        resolver.openOutputStream(uri)?.use { out ->
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            out.write(stream.toByteArray())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        overlayEffect?.close()
        effectHandlerThread.quitSafely()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val KEY_FLASH = "flash_enabled"
    }
}
