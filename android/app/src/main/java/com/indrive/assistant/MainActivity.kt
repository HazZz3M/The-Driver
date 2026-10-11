package com.indrive.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.indrive.assistant.ui.screens.CalculatorScreen
import com.indrive.assistant.ui.screens.ExpensesScreen
import com.indrive.assistant.ui.screens.OverlayControlScreen
import com.indrive.assistant.ui.screens.ShiftDashboardScreen
import com.indrive.assistant.ui.screens.SettingsScreen
import com.indrive.assistant.ui.theme.InDriveAssistantTheme
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.viewmodel.AssistantViewModel
import com.indrive.assistant.ui.viewmodel.AssistantViewModelFactory

data class NavItem(
    val title: String,
    val icon: ImageVector
)

class MainActivity : ComponentActivity() {

    private val viewModel: AssistantViewModel by viewModels {
        AssistantViewModelFactory(InDriveAssistantApp.instance.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            InDriveAssistantTheme(themeMode = themeMode) {
                MainAppContent(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun MainAppContent(viewModel: AssistantViewModel) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    val navItems = listOf(
        NavItem("الحاسبة", Icons.Default.Calculate),
        NavItem("الوردية", Icons.Default.Dashboard),
        NavItem("الزر العائم", Icons.Default.Layers),
        NavItem("المصاريف", Icons.Default.LocalGasStation),
        NavItem("الإعدادات", Icons.Default.Settings)
    )

    Scaffold(
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedIndex = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = if (isSelected) InDriveNeonGreen else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                color = if (isSelected) InDriveNeonGreen else androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Color(0x1F00EA80)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedIndex) {
            0 -> CalculatorScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            1 -> ShiftDashboardScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            2 -> OverlayControlScreen(modifier = Modifier.padding(innerPadding))
            3 -> ExpensesScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
            4 -> SettingsScreen(viewModel = viewModel, modifier = Modifier.padding(innerPadding))
        }
    }
}
