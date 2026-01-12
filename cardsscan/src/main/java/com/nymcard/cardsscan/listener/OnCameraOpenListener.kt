package com.nymcard.cardsscan.listener

/**
 * @deprecated This interface is deprecated as we've moved to CameraX.
 * CameraX handles camera operations internally.
 */
@Deprecated("Use CameraX instead")
interface OnCameraOpenListener {
    @Deprecated("CameraX handles camera initialization internally")
    fun onCameraOpen(camera: Any?)
}
