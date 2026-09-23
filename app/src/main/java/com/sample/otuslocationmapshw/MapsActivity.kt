package com.sample.otuslocationmapshw

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.sample.otuslocationmapshw.camera.CameraActivity
import com.sample.otuslocationmapshw.data.utils.LocationDataUtils
import com.sample.otuslocationmapshw.ui.theme.OtusLocationMapsTheme
import java.io.File

private const val PHOTO_ZOOM = 15f

class MapsActivity : ComponentActivity() {

    private val locationDataUtils = LocationDataUtils()
    private var photoMarkers by mutableStateOf<List<PhotoMarker>>(emptyList())
    private val cameraForResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == CameraActivity.SUCCESS_RESULT_CODE) {
            photoMarkers = loadPhotoMarkers()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        photoMarkers = loadPhotoMarkers()

        setContent {
            OtusLocationMapsTheme {
                MapsScreen(
                    photoMarkers = photoMarkers,
                    onAddPhoto = {
                        cameraForResultLauncher.launch(
                            Intent(this, CameraActivity::class.java)
                        )
                    }
                )
            }
        }
    }

    private fun loadPhotoMarkers(): List<PhotoMarker> {
        val photoFolder = File(filesDir, PHOTOS_DIRECTORY)
        return photoFolder.listFiles { file -> file.isFile }
            ?.sortedBy(File::lastModified)
            .orEmpty()
            .mapNotNull { photo ->
                runCatching {
                    val exif = ExifInterface(photo)
                    val position = readPhotoPosition(exif) ?: return@runCatching null
                    val preview = decodePreview(photo) ?: return@runCatching null
                    PhotoMarker(position, preview)
                }.onFailure { error ->
                    Log.w(TAG, "Ошибка ${photo.name}", error)
                }.getOrNull()
            }
    }

    private fun readPhotoPosition(exif: ExifInterface): LatLng? {
        val hasGpsMetadata = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null &&
            exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) != null &&
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null &&
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) != null
        if (!hasGpsMetadata) return null

        val location = locationDataUtils.getLocationFromExif(exif)
        if (
            !location.latitude.isFinite() ||
            !location.longitude.isFinite() ||
            location.latitude !in -90.0..90.0 ||
            location.longitude !in -180.0..180.0
        ) {
            return null
        }
        return LatLng(location.latitude, location.longitude)
    }

    private fun decodePreview(photo: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > PREVIEW_SIZE_PX * 2 ||
            bounds.outHeight / sampleSize > PREVIEW_SIZE_PX * 2
        ) {
            sampleSize *= 2
        }

        val decoded = BitmapFactory.decodeFile(
            photo.path,
            BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inSampleSize = sampleSize
            }
        ) ?: return null
        val preview = Bitmap.createScaledBitmap(
            decoded,
            PREVIEW_SIZE_PX,
            PREVIEW_SIZE_PX,
            true
        )
        if (preview !== decoded) decoded.recycle()
        return preview
    }

    companion object {
        private const val TAG = "MapsActivity"
        private const val PHOTOS_DIRECTORY = "photos"
        private const val PREVIEW_SIZE_PX = 64
    }
}

private data class PhotoMarker(
    val position: LatLng,
    val preview: Bitmap
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MapsScreen(
    photoMarkers: List<PhotoMarker>,
    onAddPhoto: () -> Unit
) {
    val cameraPositionState = rememberCameraPositionState()
    val lastPhotoPosition = photoMarkers.lastOrNull()?.position
    var isMapLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(isMapLoaded, lastPhotoPosition) {
        if (isMapLoaded && lastPhotoPosition != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(lastPhotoPosition, PHOTO_ZOOM)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = onAddPhoto) {
                        Text(stringResource(R.string.action_add))
                    }
                }
            )
        }
    ) { contentPadding ->
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            cameraPositionState = cameraPositionState,
            onMapLoaded = { isMapLoaded = true }
        ) {
            photoMarkers.forEach { marker ->
                val markerState = remember(marker.position) {
                    MarkerState(position = marker.position)
                }
                val markerIcon = remember(marker.preview) {
                    BitmapDescriptorFactory.fromBitmap(marker.preview)
                }
                Marker(
                    state = markerState,
                    icon = markerIcon
                )
            }
        }
    }
}