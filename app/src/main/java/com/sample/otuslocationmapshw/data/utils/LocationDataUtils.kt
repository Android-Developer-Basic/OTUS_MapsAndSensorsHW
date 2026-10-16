package com.sample.otuslocationmapshw.data.utils

import androidx.exifinterface.media.ExifInterface

class LocationDataUtils {

    fun getLocationFromExif(exif: ExifInterface): LocationPosition? {
        val coordinates = exif.latLong ?: return null
        return LocationPosition(coordinates[0], coordinates[1])
    }

    data class LocationPosition(val latitude: Double, val longitude: Double)
}
