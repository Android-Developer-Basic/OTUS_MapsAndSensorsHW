package com.sample.otuslocationmapshw.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sample.otuslocationmapshw.R
import com.sample.otuslocationmapshw.applyContentInsets
import com.sample.otuslocationmapshw.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private lateinit var imageCapture: ImageCapture

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorEventListener: SensorEventListener
    private var tiltSensor: Sensor? = null
    private var locationRequest: CancellationTokenSource? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets(binding.root)
        binding.takePhotoButton.isEnabled = false

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (tiltSensor == null) {
            binding.errorTextView.visibility = View.VISIBLE
            binding.errorTextView.text = getString(R.string.accelerometer_unavailable)
        }

        sensorEventListener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val x = event.values[0].toDouble()
                    val y = event.values[1].toDouble()
                    val z = event.values[2].toDouble()
                    binding.errorTextView.visibility =
                        if (PhoneTilt.needsAlignment(x, y, z)) View.VISIBLE else View.GONE
                }

                override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
            }

        binding.takePhotoButton.setOnClickListener { takePhoto() }

        if (allPermissionsGranted()) startCamera() else requestRequiredPermissions()
    }

    override fun onResume() {
        super.onResume()
        tiltSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        sensorManager.unregisterListener(sensorEventListener)
        super.onPause()
    }

    override fun onStop() {
        locationRequest?.let {
            it.cancel()
            locationRequest = null
            binding.takePhotoButton.isEnabled = true
        }
        super.onStop()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.permissions_required), Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto() {
        binding.takePhotoButton.isEnabled = false
        getLastLocation { location ->
            if (isDestroyed || isFinishing) return@getLastLocation
            if (!allPermissionsGranted()) {
                binding.takePhotoButton.isEnabled = true
                requestRequiredPermissions()
                return@getLastLocation
            }
            if (location == null) {
                binding.takePhotoButton.isEnabled = true
                Toast.makeText(this, R.string.location_unavailable, Toast.LENGTH_LONG).show()
                return@getLastLocation
            }

            val folder = File(filesDir, PHOTOS_DIRECTORY)
            if (!folder.exists() && !folder.mkdirs()) {
                binding.takePhotoButton.isEnabled = true
                Toast.makeText(this, R.string.photo_save_error, Toast.LENGTH_LONG).show()
                return@getLastLocation
            }
            val photoFile =
                File(
                    folder,
                    SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date()) + ".jpg",
                )

            val metadata = ImageCapture.Metadata().apply { this.location = location }
            binding.cameraPreview.display?.let { imageCapture.targetRotation = it.rotation }
            val outputFileOptions =
                ImageCapture.OutputFileOptions.Builder(photoFile).setMetadata(metadata).build()

            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        if (isDestroyed || isFinishing) return
                        Toast.makeText(
                                this@CameraActivity,
                                R.string.photo_saved,
                                Toast.LENGTH_SHORT,
                            )
                            .show()
                        setResult(SUCCESS_RESULT_CODE)
                        finish()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed", exception)
                        photoFile.delete()
                        if (isDestroyed || isFinishing) return
                        binding.takePhotoButton.isEnabled = true
                        Toast.makeText(
                                this@CameraActivity,
                                R.string.photo_save_error,
                                Toast.LENGTH_LONG,
                            )
                            .show()
                    }
                },
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (location: Location?) -> Unit) {
        if (!hasLocationPermission()) {
            callback(null)
            return
        }
        val request = CancellationTokenSource()
        locationRequest?.cancel()
        locationRequest = request
        fusedLocationClient
            .getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                    .setMaxUpdateAgeMillis(30_000)
                    .setDurationMillis(15_000)
                    .build(),
                request.token,
            )
            .addOnSuccessListener { currentLocation ->
                if (!request.token.isCancellationRequested) {
                    locationRequest = null
                    callback(currentLocation)
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Could not get current location", error)
                if (!request.token.isCancellationRequested) {
                    locationRequest = null
                    callback(null)
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(
            {
                if (isDestroyed || isFinishing) return@addListener
                val cameraProvider =
                    try {
                        cameraProviderFuture.get()
                    } catch (error: Exception) {
                        Log.e(TAG, "Could not initialize CameraX", error)
                        Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
                        return@addListener
                    }

                val preview =
                    Preview.Builder().build().also {
                        it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                    }

                imageCapture = ImageCapture.Builder().build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                    binding.takePhotoButton.isEnabled = true
                } catch (exc: Exception) {
                    Log.e(TAG, "Use case binding failed", exc)
                    Toast.makeText(this, R.string.camera_unavailable, Toast.LENGTH_LONG).show()
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun allPermissionsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED && hasLocationPermission()

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestRequiredPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
    }

    companion object {

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private const val PHOTOS_DIRECTORY = "photos"
        private val REQUIRED_PERMISSIONS =
            arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )

        const val SUCCESS_RESULT_CODE = 15
    }
}
