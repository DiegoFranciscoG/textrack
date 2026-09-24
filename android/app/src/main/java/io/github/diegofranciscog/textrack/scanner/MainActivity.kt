package io.github.diegofranciscog.textrack.scanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import io.github.diegofranciscog.textrack.scanner.ui.AppRoot
import io.github.diegofranciscog.textrack.scanner.ui.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AppRoot(viewModel) }
    }
}
