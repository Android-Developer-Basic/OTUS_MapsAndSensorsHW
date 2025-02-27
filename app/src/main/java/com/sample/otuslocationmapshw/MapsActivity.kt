package com.sample.otuslocationmapshw

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.sample.otuslocationmapshw.camera.CameraActivity
import com.sample.otuslocationmapshw.data.utils.LocationDataUtils
import com.sample.otuslocationmapshw.databinding.ActivityMapsBinding
import java.io.File

private const val REQUEST_CODE_PERMISSIONS = 1001

class MapsActivity : AppCompatActivity(), OnMapReadyCallback, OnRequestPermissionsResultCallback {

    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private val locationDataUtils = LocationDataUtils()
    private val cameraForResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CameraActivity.SUCCESS_RESULT_CODE) {
            // Обновить точки на карте при получении результата от камеры
            showPreviewsOnMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Проверяем разрешения
        checkAndRequestPermissions()

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        // Вызвать инициализацию карты
        mapFragment.getMapAsync(this)
    }

    // Запрашиваем разрешения
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
            permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        }
    }

    // Обрабатываем результат запроса
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val deniedPermissions = permissions.zip(grantResults.toList()).filter { it.second != PackageManager.PERMISSION_GRANTED }

            if (deniedPermissions.isNotEmpty()) {
                Toast.makeText(this,
                    getString(R.string.permissions_must_be_provided), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this,
                    getString(R.string.permissions_have_been_received), Toast.LENGTH_SHORT).show()
                showPreviewsOnMap()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.action_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add -> {
                cameraForResultLauncher.launch(Intent(this, CameraActivity::class.java))
                true
            }
            else -> {
                super.onOptionsItemSelected(item)
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Включаем кнопки масштабирования
        map.uiSettings.isZoomControlsEnabled = true

        // Включаем жесты масштабирования (pinch-to-zoom)
        map.uiSettings.isZoomGesturesEnabled = true

        // Включаем кнопки компаса и вращение карты
        map.uiSettings.isCompassEnabled = true
        map.uiSettings.isRotateGesturesEnabled = true
        map.uiSettings.isMapToolbarEnabled = true

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            map.isMyLocationEnabled = true // Включаем кнопку "Моё местоположение"
        }

        showPreviewsOnMap()
    }

    private fun showPreviewsOnMap() {
        map.clear()

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this,
                getString(R.string.there_is_no_permission_to_read_images), Toast.LENGTH_SHORT).show()
            return
        }

        val folder = File("${filesDir.absolutePath}/photos/")
        var lastLocation: LatLng? = null // Для хранения местоположения первого фото

        // Получаем список файлов и сортируем их по дате изменения (новейшие в конце)
        val files = folder.listFiles()
            ?.sortedBy { it.lastModified() }  // Сортируем по последней дате изменения


        files?.forEach { file ->
            val exifInterface = ExifInterface(file)
            val location = locationDataUtils.getLocationFromExif(exifInterface)
            val point = LatLng(location.latitude, location.longitude)

            val density = resources.displayMetrics.density
            val markerSize = (64 * density).toInt() // 64 dp в пикселях

            // Создаём Bitmap для маркера
            val pinBitmap = Bitmap.createScaledBitmap(
                BitmapFactory.decodeFile(
                    file.path,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }), markerSize, markerSize, false
            )

            // Создаём BitmapDescriptor
            val bitmapDescriptor = BitmapDescriptorFactory.fromBitmap(pinBitmap)

            // Добавляем маркер с иконкой
            val marker = map.addMarker(
                MarkerOptions()
                    .position(point)
                    .icon(bitmapDescriptor)  // Используем превью фотографии как иконку
            )

            marker?.tag = file.absolutePath

            // Запоминаем местоположение первого файла
            lastLocation = point
        }

        // Устанавливаем обработчик кликов на маркер
        map.setOnMarkerClickListener { marker ->
            val photoPath = marker.tag as? String
            photoPath?.let { showPhotoDialog(it) }
            true
        }

        // Если есть хотя бы одно фото, перемещаем камеру к первому фото
        lastLocation?.let { location ->
            // Применяем moveCamera для перемещения камеры
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(location, 15f)
            //map.moveCamera(cameraUpdate)

            // Или используйте animateCamera для анимации камеры
            map.animateCamera(cameraUpdate)
        }
    }

    private fun showPhotoDialog(photoPath: String) {

        // Используем MaterialAlertDialogBuilder для создания диалога
        val dialogView = layoutInflater.inflate(R.layout.dialog_photo, null)
        val imageView = dialogView.findViewById<ImageView>(R.id.dialogImageView)

        val width = imageView.width* 2
        Glide.with(this)
            .load(photoPath)
            .override(width)  // Устанавливаем ширину изображения, равную ширине контейнера
            .fitCenter()  // Масштабируем изображение по ширине контейнера
            .into(imageView)

        // Создаём MDC диалог
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)  // Устанавливаем наш кастомный layout
            .setCancelable(true)  // Позволяет закрыть диалог
            .create()

        dialog.show()
    }


}