package com.getgymdone.app

import android.app.Application
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import com.getgymdone.app.notifications.RestTimerScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GymDoneApp : Application(), ImageLoaderFactory, Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    // Hilt-aware WorkManager init (the default initializer is removed in the manifest) so the
    // Friends sync worker can have SocialRepository injected.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

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
