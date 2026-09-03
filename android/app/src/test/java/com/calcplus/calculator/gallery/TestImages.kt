package com.calcplus.calculator.gallery

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Real encoded image bytes for tests that push files through PhotoFileStore. */
object TestImages {
    fun pngBytes(width: Int = 20, height: Int = 12): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFFFF8800.toInt())
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }
}
