package com.filetransit.app

import android.app.Application

class FileTransitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}
