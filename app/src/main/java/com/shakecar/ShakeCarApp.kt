package com.shakecar

import android.app.Application
import com.shakecar.data.AppDatabase

class ShakeCarApp : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
