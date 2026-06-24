package com.getgymdone.app.ui.screens.social

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * zxing-android-embedded's default [CaptureActivity] follows the sensor orientation, so the scanner
 * opens sideways. This subclass exists only so the manifest can pin it to portrait
 * (`android:screenOrientation="portrait"`).
 */
class PortraitCaptureActivity : CaptureActivity()
