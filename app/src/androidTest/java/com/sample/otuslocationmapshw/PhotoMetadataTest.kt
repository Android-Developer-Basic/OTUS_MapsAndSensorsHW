package com.sample.otuslocationmapshw

import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import com.sample.otuslocationmapshw.data.utils.LocationDataUtils
import com.sample.otuslocationmapshw.data.utils.PhotoPreviewLoader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoMetadataTest {
    @Test
    fun gpsRoundTripPreservesCoordinatesInBothHemispheres() {
        withPhoto { file ->
            listOf(55.755826 to 37.617300, -33.868820 to -151.209296, 0.0 to 0.0).forEach {
                (lat, lon) ->
                ExifInterface(file).apply {
                    setLatLong(lat, lon)
                    saveAttributes()
                }
                val point =
                    requireNotNull(LocationDataUtils().getLocationFromExif(ExifInterface(file)))
                assertEquals(lat, point.latitude, 0.000001)
                assertEquals(lon, point.longitude, 0.000001)
            }
        }
    }

    @Test
    fun missingGpsIsNotMistakenForTheGulfOfGuinea() {
        withPhoto { file ->
            val point = LocationDataUtils().getLocationFromExif(ExifInterface(file))
            assertNull(point)
        }
    }

    @Test
    fun previewHonorsAllEightExifOrientations() {
        val red = Color.RED
        val green = Color.GREEN
        val blue = Color.BLUE
        val yellow = Color.YELLOW
        val expected =
            listOf(
                listOf(red, green, blue, yellow),
                listOf(green, red, yellow, blue),
                listOf(yellow, blue, green, red),
                listOf(blue, yellow, red, green),
                listOf(red, blue, green, yellow),
                listOf(blue, red, yellow, green),
                listOf(yellow, green, blue, red),
                listOf(green, yellow, red, blue),
            )
        withPhoto { file ->
            expected.forEachIndexed { index, colors ->
                val exif =
                    ExifInterface(file).apply {
                        setAttribute(ExifInterface.TAG_ORIENTATION, (index + 1).toString())
                        saveAttributes()
                    }
                val preview = requireNotNull(PhotoPreviewLoader.load(file, exif, 16))
                try {
                    assertEquals(16, preview.width)
                    assertEquals(16, preview.height)
                    listOf(4 to 4, 12 to 4, 4 to 12, 12 to 12).forEachIndexed { corner, (x, y) ->
                        val actual = preview.getPixel(x, y)
                        val target = colors[corner]
                        assertTrue(
                            "EXIF ${index + 1}, corner $corner",
                            kotlin.math.abs(Color.red(actual) - Color.red(target)) < 30 &&
                                kotlin.math.abs(Color.green(actual) - Color.green(target)) < 30 &&
                                kotlin.math.abs(Color.blue(actual) - Color.blue(target)) < 30,
                        )
                    }
                } finally {
                    preview.recycle()
                }
            }
        }
    }

    @Test
    fun invalidImageDoesNotCreateAPreview() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("invalid-photo-", ".jpg", context.cacheDir)
        try {
            withPhoto { valid ->
                assertNull(PhotoPreviewLoader.load(file, ExifInterface(valid), 64))
            }
        } finally {
            file.delete()
        }
    }

    private fun withPhoto(block: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File.createTempFile("metadata-test-", ".jpg", context.cacheDir)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until 80) for (x in 0 until 80) {
                bitmap.setPixel(
                    x,
                    y,
                    when {
                        y < 40 && x < 40 -> Color.RED
                        y < 40 -> Color.GREEN
                        x < 40 -> Color.BLUE
                        else -> Color.YELLOW
                    },
                )
            }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it) }
            block(file)
        } finally {
            bitmap.recycle()
            file.delete()
        }
    }
}
