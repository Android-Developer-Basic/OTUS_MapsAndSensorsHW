package com.sample.otuslocationmapshw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.sample.otuslocationmapshw.camera.CameraActivity
import com.sample.otuslocationmapshw.data.utils.LocationDataUtils
import com.sample.otuslocationmapshw.databinding.ActivityMapsBinding
import java.io.File

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding

    private val locationDataUtils = LocationDataUtils()
    private val cameraForResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == CameraActivity.SUCCESS_RESULT_CODE) {
            // Обновляем точки на карте при получении результата от камеры
            showPreviewsOnMap()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
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
        showPreviewsOnMap()
    }

    private fun showPreviewsOnMap() {
        map.clear()
        val folder = File("${filesDir.absolutePath}/photos/")

        // Проверка существования папки
        if (!folder.exists() || !folder.isDirectory) {
            Log.e("MapsActivity", "Папка с фото не существует: ${folder.absolutePath}")
            return
        }

        // Логирование количества файлов
        Log.d("MapsActivity", "Найдено файлов: ${folder.listFiles()?.size ?: 0}")

        var lastPoint: LatLng? = null

        // Перебор всех файлов в папке
        folder.listFiles()?.forEach { file ->
            if (!file.isFile) return@forEach // Пропускаем, если это не файл

            try {
                // Чтение EXIF-данных
                val exifInterface = ExifInterface(file)
                val location = locationDataUtils.getLocationFromExif(exifInterface)

                if (location != null) {
                    val point = LatLng(location.latitude, location.longitude)
                    lastPoint = point // Сохраняем последнюю точку

                    // Декодирование изображения
                    val options = BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }
                    val bitmap = BitmapFactory.decodeFile(file.path, options)

                    if (bitmap == null) {
                        Log.e("MapsActivity", "Не удалось декодировать изображение: ${file.name}")
                        return@forEach
                    }

                    // Создание уменьшенной версии изображения для маркера
                    val pinBitmap = Bitmap.createScaledBitmap(bitmap, 64, 64, false)

                    // Добавление маркера на карту
                    map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .icon(BitmapDescriptorFactory.fromBitmap(pinBitmap))
                    )
                }
            } catch (e: Exception) {
                Log.e("MapsActivity", "Ошибка при чтении EXIF-данных из файла: ${file.name}", e)
            }
        }

        // Перемещение камеры к последнему фото
        lastPoint?.let {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(it, 20f))
        }
    }
}