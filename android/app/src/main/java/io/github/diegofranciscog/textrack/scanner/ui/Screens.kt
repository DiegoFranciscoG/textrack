package io.github.diegofranciscog.textrack.scanner.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.diegofranciscog.textrack.scanner.data.local.OperatorEntity
import io.github.diegofranciscog.textrack.scanner.data.local.ReadingEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

private val Blue = Color(0xFF1F5EFF)

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val loggedIn by viewModel.loggedIn.collectAsState()
    val operator by viewModel.operator.collectAsState()
    MaterialTheme {
        when {
            !loggedIn -> LoginScreen(viewModel)
            operator == null -> OperatorScreen(viewModel)
            else -> WorkScreen(viewModel, operator!!)
        }
    }
}

@Composable
private fun LoginScreen(viewModel: MainViewModel) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("textrack", style = MaterialTheme.typography.headlineLarge, color = Blue, fontWeight = FontWeight.Bold)
        Text("Tablet de planta · lecturas de tickets", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { viewModel.login(email, password) },
            enabled = !busy && email.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (busy) "Ingresando…" else "Ingresar") }
        message?.let { MessageBanner(it) }
    }
}

@Composable
private fun OperatorScreen(viewModel: MainViewModel) {
    val operators by viewModel.operators.collectAsState()
    var filter by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("¿Quién escanea?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::logout) { Text("Salir") }
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            label = { Text("Buscar código o nombre") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        if (operators.isEmpty()) {
            Text("Sin operarios guardados. Conéctate a la red para descargarlos.")
            OutlinedButton(onClick = { viewModel.refreshOperators() }) { Text("Reintentar") }
        }
        LazyColumn {
            items(operators.filter { it.code.contains(filter, true) || it.fullName.contains(filter, true) }) { op ->
                OperatorRow(op) { viewModel.selectOperator(op) }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun OperatorRow(operator: OperatorEntity, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(operator.code, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.35f))
        Text(operator.fullName, modifier = Modifier.weight(0.5f))
        Text(operator.lineCode ?: "", modifier = Modifier.weight(0.15f))
    }
}

@Composable
private fun WorkScreen(viewModel: MainViewModel, operator: OperatorEntity) {
    var tab by rememberSaveable { mutableStateOf(0) }
    val counts by viewModel.counts.collectAsState()
    val pending = counts["PENDING"] ?: 0
    Scaffold(
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(Blue).padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(operator.code, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(operator.fullName, color = Color.White, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { viewModel.selectOperator(null) }) { Text("Cambiar", color = Color.White) }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Text("▣") }, label = { Text("Escanear") })
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text(if (pending > 0) "⟳ $pending" else "✓") },
                    label = { Text("Sincronización") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (tab == 0) ScannerTab(viewModel) else SyncTab(viewModel, counts)
        }
    }
}

@Composable
private fun ScannerTab(viewModel: MainViewModel) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    val message by viewModel.message.collectAsState()
    LaunchedEffect(message) {
        if (message != null) {
            delay(3_500)
            viewModel.clearMessage()
        }
    }
    Column(Modifier.fillMaxSize()) {
        if (granted) {
            QrCamera(onCode = viewModel::onScanned, modifier = Modifier.fillMaxWidth().weight(1f))
        } else {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("La cámara se usa solo para leer los QR de los tickets.")
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permitir cámara") }
            }
        }
        message?.let { Box(Modifier.padding(12.dp)) { MessageBanner(it) } }
    }
}

@Composable
private fun SyncTab(viewModel: MainViewModel, counts: Map<String, Int>) {
    val recent by viewModel.recent.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Counter("Pendientes", counts["PENDING"] ?: 0, Color(0xFFB26B00))
            Counter("Sincronizadas", counts["SYNCED"] ?: 0, Color(0xFF1A8A4C))
            Counter("Conflicto", counts["CONFLICT"] ?: 0, Color(0xFF5D6B7C))
            Counter("Rechazadas", counts["REJECTED"] ?: 0, Color(0xFFC62828))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Las lecturas se guardan en la tablet y se envían solas al recuperar la red.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = viewModel::syncNow) { Text("Sincronizar") }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        LazyColumn { items(recent, key = { it.clientReadingId }) { ReadingRow(it) } }
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color) {
    Card(shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text("$value", fontWeight = FontWeight.Bold, color = color, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ReadingRow(reading: ReadingEntity) {
    val time = remember(reading.scannedAtMillis) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(reading.scannedAtMillis))
    }
    val (label, color) = when (reading.status) {
        "SYNCED" -> "Enviada" to Color(0xFF1A8A4C)
        "CONFLICT" -> "Conflicto" to Color(0xFF5D6B7C)
        "REJECTED" -> "Rechazada" to Color(0xFFC62828)
        else -> "Pendiente" to Color(0xFFB26B00)
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(time, modifier = Modifier.weight(0.15f))
            Text("${reading.bundleCode} · ${reading.operationCode}", modifier = Modifier.weight(0.55f))
            Text("${reading.quantity} pzs", modifier = Modifier.weight(0.12f))
            Text(label, color = color, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.18f))
        }
        reading.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = color) }
    }
}

@Composable
private fun MessageBanner(message: UiMessage) {
    val color = when (message) {
        is UiMessage.Success -> Color(0xFF1A8A4C)
        is UiMessage.Warning -> Color(0xFFB26B00)
        is UiMessage.Error -> Color(0xFFC62828)
        is UiMessage.Info -> Blue
    }
    Text(
        message.text,
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth().background(color, RoundedCornerShape(10.dp)).padding(14.dp),
    )
}
