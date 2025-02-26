package com.sample.otuslocationmapshw.camera

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.SensorManager.SENSOR_DELAY_UI
import android.location.Location
import android.os.Bundle
import android.os.Environment
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
import com.google.common.util.concurrent.ListenableFuture
import com.sample.otuslocationmapshw.R
import com.sample.otuslocationmapshw.databinding.ActivityCameraBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class CameraActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var imageCapture: ImageCapture

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private lateinit var sensorManager: SensorManager
    private lateinit var sensorEventListener: SensorEventListener
    private var tiltSensor: Sensor? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // "Получить экземпляр SensorManager"
        sensorManager = getSystemService(SensorManager::class.java)
        //
        // "Добавить проверку на наличие датчика акселерометра и присвоить значение tiltSensor"
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        //
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val tilt = event.values[2]
                binding.errorTextView.visibility = if (abs(tilt) > 2) View.VISIBLE else View.GONE
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                //nothing to do
            }
        }

        binding.takePhotoButton.setOnClickListener {
            takePhoto()
        }
    }

    // "Подписаться на получение событий обновления датчика"
    override fun onResume() {
        super.onResume()
        if (tiltSensor == null) {
            Toast.makeText(this, getString(R.string.accelerometer_null), Toast.LENGTH_SHORT).show()
            return
        }
        sensorManager.registerListener(
            /* listener = */ sensorEventListener,
            /* sensor = */ tiltSensor,
            /* samplingPeriodUs = */ SENSOR_DELAY_UI
        )
    }

    // "Остановить получение событий от датчика"
    override fun onPause() {
        super.onPause()
        if (tiltSensor == null) return
        sensorManager.unregisterListener(sensorEventListener)
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
                Toast.makeText(
                    this,
                    getString(R.string.permissions_not_granted_by_the_user),
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto() {
        getLastLocation { location ->

            if (location == null) {
                Toast.makeText(this, getString(R.string.error_getlastlocation), Toast.LENGTH_SHORT).show()
                return@getLastLocation
            }
            Log.d("LOCATION", location.toString())

            val folderPath = "${filesDir.absolutePath}/photos/"
            val folder = File(folderPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val filePath =
                folderPath + SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date())
            Log.d("FILE_PATH", "Файл сохранен по пути: $filePath")

            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(File(filePath))
                // "4. Добавить установку местоположения в метаданные фото"
                .setMetadata(ImageCapture.Metadata().apply { this.location = location })
                .build()

            // "Добавить вызов CameraX для фото"
            // "Вывести Toast о том, что фото успешно сохранено и закрыть текущее активити c указанием кода результата SUCCESS_RESULT_CODE"
            imageCapture.takePicture(
                outputFileOptions,
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        Toast.makeText(
                            this@CameraActivity,
                            getString(R.string.photo_is_saved_toast_message),
                            Toast.LENGTH_SHORT
                        ).show()
                        setResult(SUCCESS_RESULT_CODE)
                        finish()
                    }

                    override fun onError(e: ImageCaptureException) {
                        Toast.makeText(
                            this@CameraActivity, "Error: ${e.message}", Toast.LENGTH_SHORT
                        ).show()
                    }
                })
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (location: Location?) -> Unit) {
        // "Добавить получение местоположения от fusedLocationClient и передать результат в callback после получения"
        fusedLocationClient.getCurrentLocation(
            CurrentLocationRequest.Builder().build(), null
        ).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                callback.invoke(task.result)
            } else {
                Log.e(TAG, getString(R.string.error_getlastlocation), task.exception)
                callback.invoke(null)
            } }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    companion object {

        private const val TAG = "CameraXApp"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10

        // "Указать набор требуемых разрешений"
        private val REQUIRED_PERMISSIONS: Array<String> = listOf(
            ACCESS_COARSE_LOCATION,
            ACCESS_FINE_LOCATION,
            CAMERA
        ).toTypedArray()

        const val SUCCESS_RESULT_CODE = 15
    }
}