package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.i18n.LocalWafaStrings
import com.example.ui.i18n.stringsFor
import com.example.ui.navigation.WafaAppShell
import com.example.ui.theme.WafaInboxTheme
import com.example.ui.viewmodel.WafaViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: WafaViewModel by viewModels {
        WafaViewModel.Factory((application as WafaInboxApplication).repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val strings = stringsFor(state.preferences.language)

            CompositionLocalProvider(LocalWafaStrings provides strings) {
                WafaInboxTheme(themeMode = state.preferences.themeMode) {
                    WafaAppShell(state = state, viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "wafa.inboxai" && data.host == "oauth") {
            viewModel.handleOAuthCallback(data)
        }
    }
}
