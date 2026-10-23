package com.kejing.spatialxiangqi.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.kejing.spatialxiangqi.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
