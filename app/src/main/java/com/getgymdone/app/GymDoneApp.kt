package com.getgymdone.app

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.getgymdone.app.notifications.RestTimerScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GymDoneApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        RestTimerScheduler.ensureChannel(this)
    }

    // App-wide Coil loader with animated-GIF support so uploaded GIFs play in the carousel.
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
}
