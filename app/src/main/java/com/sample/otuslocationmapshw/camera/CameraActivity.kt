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
import android.net.Uri
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
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.common.util.concurrent.ListenableFuture
import com.sample.otuslocationmapshw.R
import com.sample.otuslocationmapshw.databinding.ActivityCameraBinding
import java.io.File
import java.io.IOException
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

        // Получаем экземпляр SensorManager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Прочие действия, например, проверка разрешений и запуск камеры
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // Получить экземпляр датчика акселерометра и присвоить значение tiltSensor
        tiltSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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


    // Подписаться на получение событий обновления датчика
    override fun onResume() {
        super.onResume()
        // Подписываемся на получение данных от датчика акселерометра
        tiltSensor?.let {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    // Остановить получение событий от датчика
    override fun onPause() {
        super.onPause()
        // Отписываемся от получения данных от датчика
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
            Log.d("LOCATION", location.toString())

            val folderPath = "${filesDir.absolutePath}/photos/"
            val folder = File(folderPath)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val filePath = folderPath + SimpleDateFormat(FILENAME_FORMAT, Locale.getDefault()).format(Date()) + ".jpg"

            // Создаем объект OutputFileOptions для сохранения фотографии
            val outputFileOptionsBuilder = ImageCapture.OutputFileOptions.Builder(File(filePath))

            val outputFileOptions = outputFileOptionsBuilder.build()

            // Создаем callback для обработки результата сохранения изображения
            val imageSavedCallback = object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    // Фотография успешно сохранена
                    val savedUri = Uri.fromFile(File(filePath))
                    Log.d(TAG, getString(R.string.photo_saved, savedUri))

                    // Добавляем метаданные местоположения в EXIF
                    try {
                        val exif = ExifInterface(filePath)
                        if (location != null) {
                            exif.setLatLong(location.latitude, location.longitude)
                            exif.saveAttributes()
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, getString(R.string.error_saving_exif_metadata), e)
                    }

                    // Выводим Toast с информацией о том, что фото сохранено
                    Toast.makeText(applicationContext,
                        getString(R.string.photo_saved_to, savedUri), Toast.LENGTH_SHORT).show()

                    // Устанавливаем результат работы активити с помощью setResult
                    setResult(SUCCESS_RESULT_CODE)

                    // Завершаем активити
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    // Логируем ошибку
                    Log.e(TAG, getString(R.string.error_saving_photo), exception)

                    // Выводим Toast с сообщением об ошибке
                    Toast.makeText(applicationContext,
                        getString(
                            R.string.an_error_occurred_while_saving_the_photo,
                            exception.message
                        ), Toast.LENGTH_LONG).show()
                }
            }

            // Вызываем метод takePicture() для захвата фото
            imageCapture.takePicture(outputFileOptions, ContextCompat.getMainExecutor(this), imageSavedCallback)
        }
    }



    @SuppressLint("MissingPermission")
    private fun getLastLocation(callback: (location: Location?) -> Unit) {
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                callback.invoke(location) // или просто callback(location)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, getString(R.string.error_getting_location), exception)
                callback.invoke(null) // передаем null, если не удалось получить местоположение
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
                Log.e(TAG, getString(R.string.use_case_binding_failed), exc)
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
        // Указать набор требуемых разрешений
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        const val SUCCESS_RESULT_CODE = 15
    }
}