package com.sample.otuslocationmapshw.data.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ThumbnailUtils
import androidx.exifinterface.media.ExifInterface
import java.io.File

internal object PhotoPreviewLoader {
    fun load(photo: File, exif: ExifInterface, size: Int): Bitmap? {
        require(size > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= size) sample *= 2
        val decoded =
            BitmapFactory.decodeFile(
                photo.path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            ) ?: return null
        val transform =
            Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
        val upright =
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, transform, true)
        if (upright !== decoded) decoded.recycle()
        return ThumbnailUtils.extractThumbnail(
            upright,
            size,
            size,
            ThumbnailUtils.OPTIONS_RECYCLE_INPUT,
        )
    }
}
