package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indrive.assistant.data.model.DriverSettings
import com.indrive.assistant.data.model.VehicleProfile
import com.indrive.assistant.ui.theme.DarkBackground
import com.indrive.assistant.ui.theme.DarkCardBorder
import com.indrive.assistant.ui.theme.DarkSurface
import com.indrive.assistant.ui.theme.DarkSurfaceVariant
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.theme.TextPrimary
import com.indrive.assistant.ui.theme.TextSecondary
import com.indrive.assistant.ui.viewmodel.AssistantViewModel
import com.indrive.assistant.util.UpdateInfo
import com.indrive.assistant.util.UpdateManager
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val tabs = listOf(
        Pair("القبول التلقائي", Icons.Default.Bolt),
        Pair("شكل العلامة", Icons.Default.Palette),
        Pair("المناطق", Icons.Default.Map),
        Pair("التسعير والتشغيل", Icons.Default.AttachMoney),
        Pair("التطبيق", Icons.Default.Settings)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "الإعدادات التشغيلية والتخصيص",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "القبول والمزايدة التلقائية، ستايل الـ HUD، فلترة المناطق، وتزامن الاستهلاك",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Segmented Tabs Header
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = DarkSurface,
            contentColor = InDriveNeonGreen,
            edgePadding = 12.dp
        ) {
            tabs.forEachIndexed { index, pair ->
                val isSelected = selectedTab == index
                Tab(
                    selected = isSelected,
                    onClick = { selectedTab = index },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = pair.second,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (isSelected) InDriveNeonGreen else TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = pair.first,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) InDriveNeonGreen else TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                )
            }
        }

        // Tab Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            when (selectedTab) {
                0 -> AutoAcceptTab(viewModel = viewModel)
                1 -> OverlayStyleTab(viewModel = viewModel)
                2 -> ZonesTab(viewModel = viewModel)
                3 -> PricingTab(viewModel = viewModel)
                4 -> AppSettingsTab(viewModel = viewModel)
            }
        }
    }
}

// =============================================================================
// TAB 1: AUTO-ACCEPT & SMART BIDDING AUTOMATION (نظام القبول والمزايدة التلقائية)
// =============================================================================
@Composable
fun AutoAcceptTab(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val currentSettings by viewModel.driverSettings.collectAsState()

    var enabled by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptEnabled) }
    var minNetProfit by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptMinNetProfit.toString()) }
    var minProfitPerKm by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptMinProfitPerKm.toString()) }
    var maxPickupKm by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptMaxPickupKm.toString()) }
    var onlyFavZones by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptOnlyFavoriteZones) }
    var ignoreBlacklist by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptIgnoreBlacklist) }
    var bidMode by remember(currentSettings) { mutableStateOf(currentSettings.autoBidMode) }
    var delaySec by remember(currentSettings) { mutableStateOf(currentSettings.autoAcceptDelaySeconds.toFloat()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Master Switch Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (enabled) InDriveNeonGreen.copy(alpha = 0.12f) else DarkSurface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp,
                    if (enabled) InDriveNeonGreen else DarkCardBorder
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = if (enabled) InDriveNeonGreen else TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "نظام القبول والمزايدة التلقائية",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (enabled)
                                "النظام يعمل: قبول فوري للطلبات المطابقة لشروطك المالية بمجرد ظهورها"
                            else
                                "النظام متوقف: قم بالتفعيل لتسريع قبول المشاوير بدون لمس الهاتف",
                            color = if (enabled) InDriveNeonGreen else TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }

                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            val updated = currentSettings.copy(autoAcceptEnabled = it)
                            viewModel.saveDriverSettings(updated)
                            Toast.makeText(
                                context,
                                if (it) "تم تفعيل القبول التلقائي" else "تم إيقاف القبول التلقائي",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF070B14),
                            checkedTrackColor = InDriveNeonGreen,
                            uncheckedThumbColor = TextSecondary,
                            uncheckedTrackColor = DarkSurfaceVariant
                        )
                    )
                }
            }
        }

        // Financial Criteria Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "معايير وشروط القبول المالي",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    // Min Net Profit
                    Column {
                        SettingTextField(
                            label = "الحد الأدنى لصافي ربح الرحلة (${currentSettings.currency})",
                            value = minNetProfit,
                            onValueChange = { minNetProfit = it },
                            hint = "الربح الصافي للرحلة بعد خصم الوقود وعمولة المنصة والإهلاك"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("15", "20", "25", "30", "40").forEach { p ->
                                QuickChip(text = "$p ج.م", isSelected = minNetProfit == p) {
                                    minNetProfit = p
                                }
                            }
                        }
                    }

                    // Min Profit Per Km
                    Column {
                        SettingTextField(
                            label = "الحد الأدنى لربح الكيلومتر (${currentSettings.currency}/كم)",
                            value = minProfitPerKm,
                            onValueChange = { minProfitPerKm = it },
                            hint = "معدل الربح الصافي مقسوماً على مسافة المشوار الإجمالية"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("3.0", "4.0", "5.0", "6.0", "8.0").forEach { k ->
                                QuickChip(text = "$k ج/كم", isSelected = minProfitPerKm == k) {
                                    minProfitPerKm = k
                                }
                            }
                        }
                    }

                    // Max Pickup Distance
                    Column {
                        SettingTextField(
                            label = "أقصى مسافة وصول مقبولة للعميل (كم)",
                            value = maxPickupKm,
                            onValueChange = { maxPickupKm = it },
                            hint = "استبعاد الرحلات البعيدة التي تزيد تكلفة وصولها عن فائدتها"
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("1.5", "2.0", "3.0", "4.0", "5.0").forEach { d ->
                                QuickChip(text = "$d كم", isSelected = maxPickupKm == d) {
                                    maxPickupKm = d
                                }
                            }
                        }
                    }
                }
            }
        }

        // Zone Safety Filters Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "فلترة المناطق الجغرافية والأمان",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    // Strict Blacklist
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "حظر واستبعاد المناطق السوداء تماماً",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "رفض فوري لأي رحلة تتجه أو تبدأ من منطقة غير مرغوبة",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = !ignoreBlacklist,
                            onCheckedChange = { ignoreBlacklist = !it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF070B14),
                                checkedTrackColor = Color(0xFFFF3355)
                            )
                        )
                    }

                    Divider(color = DarkCardBorder.copy(alpha = 0.5f))

                    // Only Favorite Zones
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "قبول رحلات المناطق المفضلة فقط",
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "القبول فقط عندما تكون المنطقة ضمن قائمتك المفضلة",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Switch(
                            checked = onlyFavZones,
                            onCheckedChange = { onlyFavZones = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF070B14),
                                checkedTrackColor = Color(0xFFFFB800)
                            )
                        )
                    }
                }
            }
        }

        // Mode & Execution Timing Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "نمط التنفيذ وسرعة القبول",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    // Bidding Mode
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { bidMode = "fare" },
                            color = if (bidMode == "fare") InDriveNeonGreen.copy(alpha = 0.15f) else DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (bidMode == "fare") InDriveNeonGreen else DarkCardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "قبول فوري",
                                    color = if (bidMode == "fare") InDriveNeonGreen else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "القبول بسعر الراكب المباشر",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { bidMode = "counter_bid" },
                            color = if (bidMode == "counter_bid") Color(0xFF00D4FF).copy(alpha = 0.15f) else DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (bidMode == "counter_bid") Color(0xFF00D4FF) else DarkCardBorder
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "مزايدة ذكية",
                                    color = if (bidMode == "counter_bid") Color(0xFF00D4FF) else TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "المزايدة بأعلى خيار متاح",
                                    color = TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            }
                        }
                    }

                    // Delay Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "مهلة التأخير قبل الضغط:",
                                color = TextPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "${String.format("%.1f", delaySec)} ثانية",
                                color = InDriveNeonGreen,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = delaySec,
                            onValueChange = { delaySec = it },
                            valueRange = 0.3f..5.0f,
                            steps = 9,
                            colors = SliderDefaults.colors(
                                thumbColor = InDriveNeonGreen,
                                activeTrackColor = InDriveNeonGreen,
                                inactiveTrackColor = DarkSurfaceVariant
                            )
                        )
                        Text(
                            text = "مهلة الأمان تتيح لك قراءة العرض على الشاشة وتمنع الاشتباه بالنقر الآلي السريع.",
                            color = TextSecondary,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }

        // Save Button
        item {
            Button(
                onClick = {
                    val updated = currentSettings.copy(
                        autoAcceptEnabled = enabled,
                        autoAcceptMinNetProfit = minNetProfit.toDoubleOrNull() ?: 15.0,
                        autoAcceptMinProfitPerKm = minProfitPerKm.toDoubleOrNull() ?: 4.0,
                        autoAcceptMaxPickupKm = maxPickupKm.toDoubleOrNull() ?: 3.0,
                        autoAcceptOnlyFavoriteZones = onlyFavZones,
                        autoAcceptIgnoreBlacklist = ignoreBlacklist,
                        autoBidMode = bidMode,
                        autoAcceptDelaySeconds = delaySec.toDouble()
                    )
                    viewModel.saveDriverSettings(updated)
                    Toast.makeText(context, "تم حفظ إعدادات القبول التلقائي بنجاح", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InDriveNeonGreen,
                    contentColor = Color(0xFF070B14)
                )
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ وتطبيق إعدادات القبول التلقائي", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun QuickChip(
    text: String,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = if (isSelected) InDriveNeonGreen.copy(alpha = 0.2f) else DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) InDriveNeonGreen else DarkCardBorder
        ),
        modifier = Modifier.clickable { onSelect() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = if (isSelected) InDriveNeonGreen else TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// =============================================================================
// TAB 2: PRICING & OPERATIONAL PARAMETERS
// =============================================================================
@Composable
fun PricingTab(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val currentSettings by viewModel.driverSettings.collectAsState()

    var fuelConsumption by remember(currentSettings) { mutableStateOf(currentSettings.fuelConsumptionLPer100Km.toString()) }
    var fuelPrice by remember(currentSettings) { mutableStateOf(currentSettings.fuelPricePerLiter.toString()) }
    var commission by remember(currentSettings) { mutableStateOf(currentSettings.inDriveCommissionPercent.toString()) }
    var wearTear by remember(currentSettings) { mutableStateOf(currentSettings.wearAndTearPerKm.toString()) }
    var dailyTarget by remember(currentSettings) { mutableStateOf(currentSettings.targetDailyEarnings.toString()) }
    var minProfitKm by remember(currentSettings) { mutableStateOf(currentSettings.minProfitPerKm.toString()) }
    var currency by remember(currentSettings) { mutableStateOf(currentSettings.currency) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00D4FF).copy(alpha = 0.12f),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00D4FF).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DirectionsCar,
                        contentDescription = null,
                        tint = Color(0xFF00D4FF),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "تزامن استهلاك المركبة النشطة مع الحسابات",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "عند اختيار أي مركبة من شاشة (المركبات والمصاريف)، يتم تطبيق استهلاكها وسعر وقودها وإهلاكها تلقائياً على كل العمليات. لتسجيل استهلاك مركبة جديدة أو متابعة فواتير البنزين، استخدم قسم المصاريف.",
                            color = TextSecondary,
                            fontSize = 11.5.sp
                        )
                    }
                }
            }
        }

        item {
            Text(
                text = "سعر الوقود السريع (محطات مصر)",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FuelChip("بنزين 92", "15.25", fuelPrice) { fuelPrice = "15.25" }
                FuelChip("بنزين 95", "17.00", fuelPrice) { fuelPrice = "17.00" }
                FuelChip("بنزين 80", "13.75", fuelPrice) { fuelPrice = "13.75" }
                FuelChip("غاز طبيعي", "11.50", fuelPrice) { fuelPrice = "11.50" }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingTextField(
                        label = "سعر لتر الوقود ($currency)",
                        value = fuelPrice,
                        onValueChange = { fuelPrice = it }
                    )

                    SettingTextField(
                        label = "معدل استهلاك الوقود (لتر لكل 100 كم)",
                        value = fuelConsumption,
                        onValueChange = { fuelConsumption = it },
                        hint = "المتوسط في المدن 8.0 - 9.5 لتر/100كم"
                    )

                    SettingTextField(
                        label = "نسبة عمولة إن درايف (%)",
                        value = commission,
                        onValueChange = { commission = it },
                        hint = "المتوسط الحالي 9.5% - 10.5%"
                    )

                    SettingTextField(
                        label = "معدل إهلاك وصيانة السيارة ($currency لكل كم)",
                        value = wearTear,
                        onValueChange = { wearTear = it },
                        hint = "تشمل الكاوتش، قطع الغيار، والصيانة الدورية (~1.2 ج/كم)"
                    )

                    SettingTextField(
                        label = "الحد الأدنى المقبول لصافي الربح ($currency لكل كم)",
                        value = minProfitKm,
                        onValueChange = { minProfitKm = it },
                        hint = "أي رحلة تعطي أقل من هذا الرقم سيتم تحذيرك منها فوراً"
                    )

                    SettingTextField(
                        label = "الهدف المالي اليومي المطلوب ($currency)",
                        value = dailyTarget,
                        onValueChange = { dailyTarget = it }
                    )

                    SettingTextField(
                        label = "رمز العملة",
                        value = currency,
                        onValueChange = { currency = it },
                        keyboardType = KeyboardType.Text
                    )
                }
            }
        }

        item {
            Button(
                onClick = {
                    val newSettings = currentSettings.copy(
                        fuelConsumptionLPer100Km = fuelConsumption.toDoubleOrNull() ?: 8.5,
                        fuelPricePerLiter = fuelPrice.toDoubleOrNull() ?: 15.0,
                        inDriveCommissionPercent = commission.toDoubleOrNull() ?: 10.0,
                        wearAndTearPerKm = wearTear.toDoubleOrNull() ?: 1.2,
                        targetDailyEarnings = dailyTarget.toDoubleOrNull() ?: 1500.0,
                        minProfitPerKm = minProfitKm.toDoubleOrNull() ?: 8.0,
                        currency = currency.trim().ifEmpty { "EGP" }
                    )
                    viewModel.updateSettings(newSettings)
                    Toast.makeText(context, "تم حفظ وتطبيق معايير التسعير بنجاح", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = InDriveNeonGreen,
                    contentColor = Color(0xFF0F172A)
                )
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ وتطبيق إعدادات التسعير", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// =============================================================================
// TAB 3: OVERLAY HUD APPEARANCE (6 Styles + Size Picker)
// =============================================================================
@Composable
fun OverlayStyleTab(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val currentStyle by viewModel.overlayStyle.collectAsState()
    val currentSize by viewModel.overlaySize.collectAsState()

    val styles = listOf(
        Pair("COMPACT_CIRCLE", "قرص دائري مصغر (Coin Badge)"),
        Pair("SLIM_PILL", "كبسولة أفقية رفيعة (Slim Pill)"),
        Pair("SPEEDO_GAUGE", "عداد سرعة رقمي (Speedo Gauge)"),
        Pair("MINI_TAG", "شارة تسعير مدمجة (Price Tag)"),
        Pair("CORNER_RIBBON", "شريط زاوية أنيق (Corner Notch)"),
        Pair("GLOW_DOT", "نقطة إشعاع ذكية (Pulse Orbit)")
    )

    val sizes = listOf(
        Pair("SMALL", "صغير (42dp)"),
        Pair("MEDIUM", "متوسط متزن (52dp)"),
        Pair("LARGE", "واضح وكبير (62dp)")
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "حجم العلامة المنبثقة فوق طلبات الركوب",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = "اختر الحجم المناسب حتى لا تغطي العلامة على تفاصيل طلب الركوب الأصلي",
                color = TextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                sizes.forEach { (key, label) ->
                    val isSel = currentSize.equals(key, ignoreCase = true)
                    FilterChip(
                        selected = isSel,
                        onClick = { viewModel.setOverlaySize(key) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = InDriveNeonGreen,
                            selectedLabelColor = Color(0xFF0F172A)
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "أشكال وستايلات العلامة المنبثقة (6 ستايلات)",
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Text(
                text = "اختر الستايل الأنسب لمدى رؤيتك أثناء القيادة",
                color = TextSecondary,
                fontSize = 12.sp
            )
        }

        items(styles) { (key, title) ->
            val isSelected = currentStyle.equals(key, ignoreCase = true)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        viewModel.setOverlayStyle(key)
                        Toast.makeText(context, "تم تطبيق ستايل '$title'", Toast.LENGTH_SHORT).show()
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (isSelected) InDriveNeonGreen else DarkCardBorder
                    )
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) InDriveNeonGreen else TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "السعر العادل يظهر بوضوح وبأقل استهلاك لمساحة الشاشة",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }

                    // Mini live preview box
                    Surface(
                        modifier = Modifier.size(54.dp),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0A0F1D),
                        border = androidx.compose.foundation.BorderStroke(2.dp, if (isSelected) InDriveNeonGreen else Color(0xFF38BDF8))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "85ج",
                                color = if (isSelected) InDriveNeonGreen else Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// TAB 4: GEOGRAPHIC ZONES (FAVORITES & BLACKLIST)
// =============================================================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZonesTab(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val currentSettings by viewModel.driverSettings.collectAsState()

    val favoriteZones = remember(currentSettings) {
        mutableStateListOf<String>().apply { addAll(currentSettings.favoriteZones) }
    }
    val blacklistedZones = remember(currentSettings) {
        mutableStateListOf<String>().apply { addAll(currentSettings.blacklistedZones) }
    }

    var newFavoriteText by remember { mutableStateOf("") }
    var newBlacklistText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Favorite Zones
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B).copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المناطق المفضلة (مع وميض ضوئي)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    }
                    Text(
                        text = "تظهر بوميض وإشعاع ضوئي ذهبي لتمييزها فوراً عن أي ألوان أخرى",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteZones.forEach { zone ->
                            ZoneChip(
                                name = zone,
                                color = Color(0xFFF59E0B),
                                onDelete = {
                                    favoriteZones.remove(zone)
                                    viewModel.updateSettings(currentSettings.copy(favoriteZones = favoriteZones.toList()))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newFavoriteText,
                            onValueChange = { newFavoriteText = it },
                            placeholder = { Text("أدخل منطقة مفضلة (مثال: التجمع)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val t = newFavoriteText.trim()
                                if (t.isNotEmpty() && !favoriteZones.contains(t)) {
                                    favoriteZones.add(t)
                                    viewModel.updateSettings(currentSettings.copy(favoriteZones = favoriteZones.toList()))
                                    newFavoriteText = ""
                                    Toast.makeText(context, "تمت إضافة '$t' للمفضلة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                                .size(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A))
                        }
                    }
                }
            }
        }

        // Blacklist Zones
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFDC2626).copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("المناطق السوداء / غير المرغوبة", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    }
                    Text(
                        text = "تظهر بتحذير أحمر صريح لرفضها إلا إذا كان السعر استثنائياً",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        blacklistedZones.forEach { zone ->
                            ZoneChip(
                                name = zone,
                                color = Color(0xFFDC2626),
                                onDelete = {
                                    blacklistedZones.remove(zone)
                                    viewModel.updateSettings(currentSettings.copy(blacklistedZones = blacklistedZones.toList()))
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = newBlacklistText,
                            onValueChange = { newBlacklistText = it },
                            placeholder = { Text("أدخل منطقة غير مرغوبة (مثال: حلوان)", fontSize = 12.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                val t = newBlacklistText.trim()
                                if (t.isNotEmpty() && !blacklistedZones.contains(t)) {
                                    blacklistedZones.add(t)
                                    viewModel.updateSettings(currentSettings.copy(blacklistedZones = blacklistedZones.toList()))
                                    newBlacklistText = ""
                                    Toast.makeText(context, "تمت إضافة '$t' للمناطق السوداء", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier
                                .background(Color(0xFFDC2626), RoundedCornerShape(12.dp))
                                .size(50.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color.White)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// =============================================================================
// TAB 5: APP SETTINGS, THEME & IN-APP UPDATES
// =============================================================================
@Composable
fun AppSettingsTab(viewModel: AssistantViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val themeMode by viewModel.themeMode.collectAsState()

    val currentAppVersion = "v1.5.1"
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Theme Selector
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = InDriveNeonGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("مظهر التطبيق (Dark / Light Theme)", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                    }
                    Text(
                        text = "التبديل بين الوضع الليلي المظلم عالي التباين والوضع النهاري الفاتح",
                        color = TextSecondary,
                        fontSize = 11.5.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = themeMode.equals("dark", ignoreCase = true),
                            onClick = { viewModel.setThemeMode("dark") },
                            label = { Text("الوضع الداكن (Dark)") },
                            leadingIcon = { Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = InDriveNeonGreen, selectedLabelColor = Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f)
                        )

                        FilterChip(
                            selected = themeMode.equals("light", ignoreCase = true),
                            onClick = { viewModel.setThemeMode("light") },
                            label = { Text("الوضع الفاتح (Light)") },
                            leadingIcon = { Icon(imageVector = Icons.Default.LightMode, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = InDriveNeonGreen, selectedLabelColor = Color(0xFF0F172A)),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Live In-App Update
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8).copy(alpha = 0.5f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("تحديثات التطبيق التلقائية", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 15.sp)
                            Text("الإصدار الحالي: $currentAppVersion (بناء 7)", color = TextSecondary, fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (!isCheckingUpdate && !isDownloadingUpdate) {
                                    isCheckingUpdate = true
                                    updateCheckMessage = null
                                    coroutineScope.launch {
                                        val result = UpdateManager.checkForUpdate(currentAppVersion)
                                        isCheckingUpdate = false
                                        result.onSuccess { info ->
                                            updateResult = info
                                            if (!info.isAvailable) {
                                                updateCheckMessage = "أنت تستخدم أحدث إصدار بالفعل ($currentAppVersion)."
                                            }
                                        }.onFailure { err ->
                                            updateCheckMessage = "تعذر الاتصال بسيرفر التحديثات: ${err.message}"
                                        }
                                    }
                                }
                            },
                            enabled = !isCheckingUpdate && !isDownloadingUpdate,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color(0xFF38BDF8)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (isCheckingUpdate) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF38BDF8), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("جاري الفحص...", fontSize = 12.sp)
                            } else {
                                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("فحص التحديثات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    updateCheckMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(text = msg, color = Color(0xFF38BDF8), fontSize = 12.sp)
                    }

                    val info = updateResult
                    if (info != null && info.isAvailable) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0F172A),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, InDriveNeonGreen)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null, tint = InDriveNeonGreen)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("يتوفر إصدار جديد: ${info.versionName}", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(text = info.releaseNotes, color = TextSecondary, fontSize = 12.sp)

                                Spacer(modifier = Modifier.height(12.dp))

                                if (isDownloadingUpdate) {
                                    Column {
                                        LinearProgressIndicator(
                                            progress = downloadProgress / 100f,
                                            modifier = Modifier.fillMaxWidth().height(8.dp),
                                            color = InDriveNeonGreen,
                                            trackColor = DarkSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("جاري تحميل التحديث: $downloadProgress%", color = InDriveNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isDownloadingUpdate = true
                                            downloadProgress = 0
                                            coroutineScope.launch {
                                                val dlResult = UpdateManager.downloadApk(context, info.downloadUrl) { progress ->
                                                    downloadProgress = progress
                                                }
                                                isDownloadingUpdate = false
                                                dlResult.onSuccess { apkFile ->
                                                    Toast.makeText(context, "اكتمل التحميل، جاري فتح التثبيت...", Toast.LENGTH_SHORT).show()
                                                    UpdateManager.installApk(context, apkFile)
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "فشل التحميل: ${err.message}", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(46.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = InDriveNeonGreen, contentColor = Color(0xFF0F172A)),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudDownload, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("تثبيت التحديث الآن من داخل البرنامج", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // App System Info
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("The Driver - نظام مساعد الكابتن الذكي", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("الإصدار: v1.5.1 (بناء 7) • محرك تحليل تسعير فوري • صفر استهلاك للبطارية في وضع الخمول", color = TextSecondary, fontSize = 11.5.sp)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// Common Sub-components
@Composable
fun ZoneChip(
    name: String,
    color: Color,
    onDelete: () -> Unit
) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                tint = color,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { onDelete() }
            )
        }
    }
}

@Composable
fun FuelChip(
    name: String,
    price: String,
    currentPrice: String,
    onSelect: () -> Unit
) {
    val isSelected = currentPrice == price
    Surface(
        color = if (isSelected) InDriveNeonGreen.copy(alpha = 0.2f) else DarkSurface,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isSelected) InDriveNeonGreen else DarkCardBorder
        ),
        modifier = Modifier.clickable { onSelect() }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = name, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(text = "$price ج.م", color = if (isSelected) InDriveNeonGreen else TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
fun SettingTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = InDriveNeonGreen,
                unfocusedBorderColor = DarkCardBorder
            ),
            shape = RoundedCornerShape(12.dp)
        )
        hint?.let {
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = it, color = TextSecondary, fontSize = 11.sp)
        }
    }
}
