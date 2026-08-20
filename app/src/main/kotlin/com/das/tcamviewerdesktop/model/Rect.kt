package com.das.tcamviewerdesktop.model

/** Stand-in for android.graphics.Rect (left/top/right/bottom, not java.awt.Rectangle's
 *  x/y/width/height) so the ported CameraUtils/ImageDto/CameraViewModel logic below needs no
 *  semantic changes at any call site. */
data class Rect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
