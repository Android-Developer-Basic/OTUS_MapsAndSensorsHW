package com.sample.otuslocationmapshw.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sample.otuslocationmapshw.R
import com.sample.otuslocationmapshw.ui.theme.OtusLocationMapsTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CameraActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var imageCapture: ImageCapture
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var sensorManager: SensorManager
    private lateinit var sensorEventListener: SensorEventListener
    private var tiltSensor: Sensor? = null
    private var locationRequestCancellation: CancellationTokenSource? = null
    private var isTilted by mutableStateOf(false)
    private var isCaptureEnabled by mutableStateOf(false)
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            Toast.makeText(this, R.string.permissions_not_granted, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        previewView = PreviewView(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        sensorEventListener = createSensorEventListener()

        setContent {
            OtusLocationMapsTheme {
                CameraScreen(
                    previewView = previewView,
                    isTilted = isTilted,
                    isCaptureEnabled = isCaptureEnabled,
                    onTakePhoto = ::takePhoto
                )
            }
        }

        if (tiltSensor == null) {
            Toast.makeText(this, R.string.accelerometer_not_available, Toast.LENGTH_LONG).show()
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun createSensorEventListener() = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

            val tilt = event.values[2]
            isTilted = abs(tilt) > MAX_ALLOWED_Z_ACCELERATION
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
    }

    override fun onResume() {
        super.onResume()
        tiltSensor?.let { sensor ->
            sensorManager.registerListener(
                sensorEventListener,
                sensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(sensorEventListener)
        super.onPause()
    }

    override fun onDestroy() {
        locationRequestCancellation?.cancel()
        locationRequestCancellation = null
        super.onDestroy()
    }

    private fun takePhoto() {
        if (!::imageCapture.isInitialized) {
            Toast.makeText(this, R.string.camera_not_ready, Toast.LENGTH_SHORT).show()
            return
        }
        if (!hasLocationPermission()) {
            Toast.makeText(this, R.string.location_permission_not_granted, Toast.LENGTH_SHORT)
                .show()
            return
        }

        isCaptureEnabled = false
        getLastLocation { location ->
            if (location == null) {
                Toast.makeText(this, R.string.location_not_available, Toast.LENGTH_LONG).show()
                isCaptureEnabled = true
                return@getLastLocation
            }

            val folder = File(filesDir, PHOTOS_DIRECTORY)
            if (!folder.exists() && !folder.mkdirs()) {
                Toast.makeText(this, R.string.photo_directory_error, Toast.LENGTH_LONG).show()
                isCaptureEnabled = true
                return@getLastLocation
            }
            val fileName = SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date())
            val photoFile = File(folder, "$fileName.jpg")
            val metadata = ImageCapture.Metadata().apply {
                this.location = location
            }
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(
                        outputFileResults: ImageCapture.OutputFileResults
                    ) {
                        Toast.makeText(
                            this@CameraActivity,
                            R.string.photo_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(SUCCESS_RESULT_CODE)
                        finish()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Ошибка", exception)
                        Toast.makeText(
                            this@CameraActivity,
                            R.string.photo_capture_error,
                            Toast.LENGTH_LONG
                        ).show()
                        isCaptureEnabled = true
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (location: Location?) -> Unit) {
        locationRequestCancellation?.cancel()
        val cancellation = CancellationTokenSource()
        locationRequestCancellation = cancellation

        fusedLocationClient
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnCompleteListener { task ->
                if (locationRequestCancellation === cancellation) {
                    locationRequestCancellation = null
                }
                if (!task.isSuccessful) {
                    Log.w(TAG, "Ошибка", task.exception)
                }
                if (!isDestroyed) {
                    callback(if (task.isSuccessful) task.result else null)
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder()
                    .build()
                    .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                imageCapture = ImageCapture.Builder().build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                isCaptureEnabled = true
            } catch (exception: Exception) {
                Log.e(TAG, "Ошибка", exception)
                isCaptureEnabled = false
                Toast.makeText(this, R.string.camera_start_error, Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted(): Boolean {
        return hasPermission(Manifest.permission.CAMERA) && hasLocationPermission()
    }

    private fun hasLocationPermission(): Boolean {
        return hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTOS_DIRECTORY = "photos"
        private const val MAX_ALLOWED_Z_ACCELERATION = 2f

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        const val SUCCESS_RESULT_CODE = 15
    }
}

@Composable
private fun CameraScreen(
    previewView: PreviewView,
    isTilted: Boolean,
    isCaptureEnabled: Boolean,
    onTakePhoto: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        if (isTilted) {
            Text(
                text = stringResource(R.string.aligne_your_phone),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .padding(16.dp)
            )
        }

        Button(
            onClick = onTakePhoto,
            enabled = isCaptureEnabled,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
        ) {
            Text(stringResource(R.string.take_photo))
        }
    }
}