package com.sample.otuslocationmapshw.camera

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
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.common.util.concurrent.ListenableFuture
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

        // Получение экземпляра SensorManager для работы с датчиками
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        // Получение датчика акселерометра
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
        }, ContextCompat.getMainExecutor(this))

        sensorEventListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Отслеживание угла наклона по оси X (values[0])
                val tiltX = event.values[0]
                // Если телефон наклонен слишком сильно (больше 2 единиц), показываем предупреждение
                binding.errorTextView.visibility = if (abs(tiltX) > 2) View.VISIBLE else View.GONE
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
                // Ничего не делаем при изменении точности датчика
            }
        }

        binding.takePhotoButton.setOnClickListener {
            takePhoto()
        }
    }

    override fun onResume() {
        super.onResume()
        // Подписываемся на получение событий обновления датчика акселерометра
        tiltSensor?.let {
            sensorManager.registerListener(
                sensorEventListener,
                it,
                SensorManager.SENSOR_DELAY_NORMAL
            )
        }
    }

    override fun onPause() {
        super.onPause()
        // Отписываемся от получения событий от датчика для экономии ресурсов
        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun takePhoto() {
        getLastLocation { location ->
            Log.d("LOCATION", location.toString())

            val folderPath = "${filesDir.absolutePath}/photos/"
            val folder = File(folderPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val filePath = folderPath + SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date())

            // Создание метаданных с местоположением для фото
            val metadata = ImageCapture.Metadata().apply {
                // Установка местоположения в метаданные фото
                location?.let {
                    this.location = it
                }
            }

            // Создание настроек для сохранения фото с метаданными
            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(File(filePath))
                .setMetadata(metadata) // Установка метаданных с местоположением
                .build()

            // Получение главного executor для выполнения callback-ов
            val executor = ContextCompat.getMainExecutor(this)

            // Вызов CameraX для создания фото
            imageCapture.takePicture(
                outputFileOptions,
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        // Успешное сохранение фото
                        Toast.makeText(
                            this@CameraActivity,
                            "Фото успешно сохранено!",
                            Toast.LENGTH_SHORT
                        ).show()
                        // Установка результата работы активити
                        setResult(SUCCESS_RESULT_CODE)
                        // Закрытие активити
                        finish()
                    }

                    override fun onError(exception: ImageCaptureException) {
                        // Обработка ошибки при сохранении фото
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                        Toast.makeText(
                            this@CameraActivity,
                            "Ошибка при сохранении фото: ${exception.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (location: Location?) -> Unit) {
        // Получение текущего местоположения через FusedLocationProviderClient
        // Используем getLastLocation() для получения последнего известного местоположения
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                // Передаем полученное местоположение в callback
                callback.invoke(location)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error getting location", exception)
                // В случае ошибки передаем null
                callback.invoke(null)
            }
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
        // Набор требуемых разрешений для работы с камерой и местоположением
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        )

        const val SUCCESS_RESULT_CODE = 15
    }
}