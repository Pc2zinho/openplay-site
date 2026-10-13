package com.pc2zinho.escalafoto

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

private val DeepBackground = Color(0xFF0B1114)
private val SurfaceDark = Color(0xFF121B20)
private val Accent = Color(0xFF56E39F)
private val AccentSoft = Color(0xFF1F5D46)
private val TextPrimary = Color(0xFFF3F7F5)

enum class AppScreen(val label: String, val icon: ImageVector) {
    HOME("Início", Icons.Filled.Home),
    CALENDAR("Agenda", Icons.Filled.CalendarMonth),
    IMPORT("Foto", Icons.Filled.AddAPhoto),
    EARNINGS("Ganhos", Icons.Filled.Payments),
    SETTINGS("Perfil", Icons.Filled.Settings)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val storage = ShiftStorage(this)
        setContent {
            EscalaFotoTheme {
                EscalaFotoApp(storage)
            }
        }
    }
}

@Composable
fun EscalaFotoTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Accent,
        secondary = Color(0xFF8BE8BF),
        tertiary = Color(0xFFFFC857),
        background = DeepBackground,
        surface = SurfaceDark,
        surfaceVariant = Color(0xFF18242A),
        onPrimary = Color(0xFF042116),
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        onSurfaceVariant = Color(0xFFB8C8C1),
        outline = Color(0xFF355047)
    )
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun EscalaFotoApp(storage: ShiftStorage) {
    var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
    var shifts by remember { mutableStateOf(storage.load()) }

    fun persist(newShifts: List<Shift>) {
        shifts = newShifts
            .distinctBy { listOf(it.date, it.start, it.end, it.dayOff) }
            .sortedBy { it.date }
        storage.save(shifts)
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0E171B)) {
                AppScreen.entries.forEach { screen ->
                    NavigationBarItem(
                        selected = currentScreen == screen,
                        onClick = { currentScreen = screen },
                        icon = { androidx.compose.material3.Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Accent,
                            selectedTextColor = Accent,
                            indicatorColor = AccentSoft,
                            unselectedIconColor = Color(0xFF8AA097),
                            unselectedTextColor = Color(0xFF8AA097)
                        )
                    )
                }
            }
        }
    ) { padding ->
        when (currentScreen) {
            AppScreen.HOME -> HomeScreen(
                padding = padding,
                shifts = shifts,
                onImportClick = { currentScreen = AppScreen.IMPORT }
            )
            AppScreen.CALENDAR -> CalendarScreen(padding = padding, shifts = shifts)
            AppScreen.IMPORT -> ImportScreen(
                padding = padding,
                onSave = { imported ->
                    persist(shifts + imported)
                    currentScreen = AppScreen.CALENDAR
                }
            )
            AppScreen.EARNINGS -> EarningsScreen(padding = padding, shifts = shifts)
            AppScreen.SETTINGS -> SettingsScreen(
                padding = padding,
                shifts = shifts,
                onClear = {
                    storage.clear()
                    shifts = emptyList()
                }
            )
        }
    }
}
