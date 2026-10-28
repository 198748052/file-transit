package com.filetransit.app

import android.content.Context
import com.filetransit.app.data.ApiClient
import com.filetransit.app.data.Repository
import com.filetransit.app.data.SessionStore
import com.filetransit.app.data.UploadManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow

object Graph {
    lateinit var session: SessionStore
        private set
    lateinit var repo: Repository
        private set
    lateinit var uploads: UploadManager
        private set
    lateinit var appScope: CoroutineScope
        private set

    val authEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val appContext = context.applicationContext
            session = SessionStore(appContext)
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val api = ApiClient.create(session) {
                session.clearToken()
                authEvents.tryEmit(Unit)
            }
            repo = Repository(api, session)
            uploads = UploadManager(appContext, repo, session, appScope)
            initialized = true
        }
    }
}
