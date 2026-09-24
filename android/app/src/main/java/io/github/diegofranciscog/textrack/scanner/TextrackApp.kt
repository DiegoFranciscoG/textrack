package io.github.diegofranciscog.textrack.scanner

import android.app.Application
import android.content.Context
import io.github.diegofranciscog.textrack.scanner.data.ReadingRepository
import io.github.diegofranciscog.textrack.scanner.data.local.AppDatabase
import io.github.diegofranciscog.textrack.scanner.data.remote.ApiClient
import io.github.diegofranciscog.textrack.scanner.data.remote.ApiService
import io.github.diegofranciscog.textrack.scanner.data.security.TokenStore
import io.github.diegofranciscog.textrack.scanner.sync.SyncWorker

class TextrackApp : Application() {

    lateinit var locator: ServiceLocator
        private set

    override fun onCreate() {
        super.onCreate()
        locator = ServiceLocator(this)
        SyncWorker.schedulePeriodic(this)
    }
}

/** Inyección de dependencias manual: la app es pequeña y así no depende de un framework extra. */
class ServiceLocator(context: Context) {
    val tokens = TokenStore(context)
    val database = AppDatabase.create(context)
    val api: ApiService = ApiClient.create(BuildConfig.API_BASE_URL, tokens)
    val readings = ReadingRepository(database.readings(), api, deviceId = { tokens.deviceId })
}
