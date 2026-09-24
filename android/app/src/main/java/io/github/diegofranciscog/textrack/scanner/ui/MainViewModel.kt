package io.github.diegofranciscog.textrack.scanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.diegofranciscog.textrack.scanner.TextrackApp
import io.github.diegofranciscog.textrack.scanner.data.ScanOutcome
import io.github.diegofranciscog.textrack.scanner.data.local.OperatorEntity
import io.github.diegofranciscog.textrack.scanner.data.local.ReadingEntity
import io.github.diegofranciscog.textrack.scanner.data.remote.LoginRequest
import io.github.diegofranciscog.textrack.scanner.sync.SyncWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val locator = (application as TextrackApp).locator

    private val _loggedIn = MutableStateFlow(locator.tokens.hasSession())
    val loggedIn: StateFlow<Boolean> = _loggedIn.asStateFlow()

    private val _operator = MutableStateFlow<OperatorEntity?>(null)
    val operator: StateFlow<OperatorEntity?> = _operator.asStateFlow()

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val operators: StateFlow<List<OperatorEntity>> = locator.database.operators().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recent: StateFlow<List<ReadingEntity>> = locator.database.readings().observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<Map<String, Int>> = locator.database.readings().observeCounts()
        .map { list -> list.associate { it.status to it.total } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        if (_loggedIn.value) refreshOperators()
    }

    fun login(email: String, password: String) = viewModelScope.launch {
        _busy.value = true
        runCatching { locator.api.login(LoginRequest(email.trim(), password)) }
            .onSuccess {
                if (it.user.role != "SCANNER" && it.user.role != "SUPERVISOR" && it.user.role != "ADMIN") {
                    _message.value = UiMessage.Error("Este usuario no puede registrar lecturas")
                } else {
                    locator.tokens.save(it.accessToken, it.refreshToken, it.user.fullName)
                    _loggedIn.value = true
                    refreshOperators()
                }
            }
            .onFailure { _message.value = UiMessage.Error(errorText(it)) }
        _busy.value = false
    }

    fun logout() {
        locator.tokens.clear()
        _operator.value = null
        _loggedIn.value = false
    }

    fun refreshOperators() = viewModelScope.launch {
        runCatching { locator.api.operators() }
            .onSuccess { list ->
                locator.database.operators().replaceAll(
                    list.filter { it.active }.map { OperatorEntity(it.code, it.fullName, it.lineCode) },
                )
            }
            .onFailure { _message.value = UiMessage.Info("Sin conexión: se usa la lista de operarios guardada") }
    }

    fun selectOperator(operator: OperatorEntity?) {
        _operator.value = operator
    }

    fun onScanned(raw: String) = viewModelScope.launch {
        val operatorCode = _operator.value?.code ?: return@launch
        _message.value = when (val outcome = locator.readings.registerScan(raw, operatorCode)) {
            is ScanOutcome.Queued -> {
                SyncWorker.enqueueNow(getApplication())
                UiMessage.Success(
                    "${outcome.payload.bundleCode} · ${outcome.payload.operationCode} · ${outcome.payload.quantity} piezas",
                )
            }
            is ScanOutcome.AlreadyScanned -> UiMessage.Warning(
                "Ticket ya escaneado en esta tablet" + (outcome.operatorCode?.let { " por $it" } ?: ""),
            )
            ScanOutcome.Invalid -> UiMessage.Error("El QR no es un ticket de textrack")
        }
    }

    fun syncNow() {
        SyncWorker.enqueueNow(getApplication())
        _message.value = UiMessage.Info("Sincronización encolada: se enviará en cuanto haya red")
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun errorText(error: Throwable): String = when {
        error is HttpException && error.code() == 401 -> "Credenciales inválidas"
        error is HttpException && error.code() == 429 -> "Demasiados intentos, espera un minuto"
        error is java.io.IOException -> "Sin conexión con el servidor"
        else -> "No se pudo iniciar sesión"
    }
}

sealed interface UiMessage {
    val text: String

    data class Success(override val text: String) : UiMessage
    data class Warning(override val text: String) : UiMessage
    data class Error(override val text: String) : UiMessage
    data class Info(override val text: String) : UiMessage
}
