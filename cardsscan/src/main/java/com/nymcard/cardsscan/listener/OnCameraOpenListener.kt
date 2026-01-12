package com.nymcard.cardsscan.listener

import android.hardware.Camera

interface OnCameraOpenListener {
    fun onCameraOpen(camera: Camera?)
}
