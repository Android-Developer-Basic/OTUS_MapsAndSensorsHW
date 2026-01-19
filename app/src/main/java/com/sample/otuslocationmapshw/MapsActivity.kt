package com.sample.otuslocationmapshw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
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
            // Обновление точек на карте при успешном сохранении нового фото
            if (::map.isInitialized) {
                showPreviewsOnMap()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        // Асинхронная инициализация карты через getMapAsync()
        // Передаем this (MapsActivity реализует OnMapReadyCallback)
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
        folder.listFiles()?.forEach {
            val exifInterface = ExifInterface(it)
            val location = locationDataUtils.getLocationFromExif(exifInterface)
            val point = LatLng(location.latitude, location.longitude)
            val pinBitmap = Bitmap.createScaledBitmap(
                BitmapFactory.decodeFile(
                    it.path,
                    BitmapFactory.Options().apply {
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    }), 64, 64, false
            )
            // Добавление маркера с превью фото в качестве иконки
            map.addMarker(
                MarkerOptions()
                    .position(point)
                    // Установка превью как иконки маркера
                    .icon(BitmapDescriptorFactory.fromBitmap(pinBitmap))
            )
        }
        // Перемещение камеры к местоположению последнего фото с анимацией
        folder.listFiles()?.lastOrNull()?.let { lastFile ->
            val exifInterface = ExifInterface(lastFile)
            val location = locationDataUtils.getLocationFromExif(exifInterface)
            val lastPoint = LatLng(location.latitude, location.longitude)
            // Анимация перемещения камеры к точке с зумом 15
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(lastPoint, 15f))
        }
    }
}