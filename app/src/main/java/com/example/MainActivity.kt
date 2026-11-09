package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.di.DefaultAppContainer
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.ui.navigation.HikariNavHost
import com.example.ui.theme.HikariTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContainer = (application as? HikariApp)?.container
            ?: DefaultAppContainer(applicationContext)

        setContent {
            val settings by appContainer.settingsRepository.settingsFlow.collectAsStateWithLifecycle(
                initialValue = UserSettings()
            )
            val systemDark = isSystemInDarkTheme()
            val useDarkTheme = when (settings.themeMode) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> systemDark
            }

            HikariTheme(darkTheme = useDarkTheme) {
                HikariNavHost(appContainer = appContainer)
            }
        }
    }
}
