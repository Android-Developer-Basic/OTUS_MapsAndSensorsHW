package com.sample.otuslocationmapshw

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.sample.otuslocationmapshw.camera.CameraActivity
import com.sample.otuslocationmapshw.data.utils.LocationDataUtils
import com.sample.otuslocationmapshw.data.utils.PhotoPreviewLoader
import com.sample.otuslocationmapshw.databinding.ActivityMapsBinding
import java.io.File
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var map: GoogleMap
    private lateinit var binding: ActivityMapsBinding
    private var previewsJob: Job? = null

    private val locationDataUtils = LocationDataUtils()
    private val cameraForResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == CameraActivity.SUCCESS_RESULT_CODE) {
                if (::map.isInitialized) showPreviewsOnMap()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMapsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyContentInsets(binding.root)
        setSupportActionBar(binding.toolbar)
        binding.mapsKeyStatus.visibility =
            if (BuildConfig.MAPS_API_KEY.isBlank() || BuildConfig.MAPS_API_KEY == "YOUR_API_KEY")
                View.VISIBLE
            else View.GONE

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
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
        previewsJob?.cancel()
        previewsJob =
            lifecycleScope.launch {
                val previews = withContext(Dispatchers.IO) { loadPreviews() }
                map.clear()
                previews.forEach { (point, bitmap) ->
                    map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .icon(BitmapDescriptorFactory.fromBitmap(bitmap))
                    )
                }
                previews.lastOrNull()?.first?.let { point ->
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(point, PHOTO_ZOOM))
                }
            }
    }

    private suspend fun loadPreviews(): List<Pair<LatLng, Bitmap>> {
        val folder = File(filesDir, "photos")
        val photos =
            folder
                .listFiles()
                ?.filter { it.isFile && it.extension.equals("jpg", ignoreCase = true) }
                ?.sortedBy(File::lastModified)
                .orEmpty()

        val previews = mutableListOf<Pair<LatLng, Bitmap>>()
        photos.forEach { photo ->
            coroutineContext.ensureActive()
            val exifInterface = runCatching { ExifInterface(photo) }.getOrNull() ?: return@forEach
            val location = locationDataUtils.getLocationFromExif(exifInterface) ?: return@forEach
            if (
                !location.latitude.isFinite() ||
                    !location.longitude.isFinite() ||
                    location.latitude !in -90.0..90.0 ||
                    location.longitude !in -180.0..180.0
            )
                return@forEach
            val point = LatLng(location.latitude, location.longitude)
            val pinBitmap =
                PhotoPreviewLoader.load(photo, exifInterface, PREVIEW_SIZE_PX) ?: return@forEach
            previews.add(point to pinBitmap)
        }
        return previews
    }

    private companion object {
        const val PREVIEW_SIZE_PX = 64
        const val PHOTO_ZOOM = 14f
    }
}
