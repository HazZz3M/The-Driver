package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentSettings by viewModel.driverSettings.collectAsState()

    var fuelConsumption by remember(currentSettings) { mutableStateOf(currentSettings.fuelConsumptionLPer100Km.toString()) }
    var fuelPrice by remember(currentSettings) { mutableStateOf(currentSettings.fuelPricePerLiter.toString()) }
    var commission by remember(currentSettings) { mutableStateOf(currentSettings.inDriveCommissionPercent.toString()) }
    var wearTear by remember(currentSettings) { mutableStateOf(currentSettings.wearAndTearPerKm.toString()) }
    var dailyTarget by remember(currentSettings) { mutableStateOf(currentSettings.targetDailyEarnings.toString()) }
    var minProfitKm by remember(currentSettings) { mutableStateOf(currentSettings.minProfitPerKm.toString()) }
    var currency by remember(currentSettings) { mutableStateOf(currentSettings.currency) }

    // Zone Lists State
    val favoriteZones = remember(currentSettings) {
        mutableStateListOf<String>().apply { addAll(currentSettings.favoriteZones) }
    }
    val blacklistedZones = remember(currentSettings) {
        mutableStateListOf<String>().apply { addAll(currentSettings.blacklistedZones) }
    }

    var newFavoriteText by remember { mutableStateOf("") }
    var newBlacklistText by remember { mutableStateOf("") }

    // In-App Update State
    val currentAppVersion = "v1.4.0"
    var isCheckingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateCheckMessage by remember { mutableStateOf<String?>(null) }
    var isDownloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Screen Header
        Text(
            text = "إعدادات المركبة والتسعير التشغيلي",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            text = "تخصيص أسعار الوقود، المناطق المفضلة والمحظورة، وتحديثات التطبيق",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // =========================================================================
        // 1. IN-APP AUTO-UPDATE SECTION
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8).copy(alpha = 0.5f))
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "تحديثات التطبيق المباشرة",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "الإصدار المثبت: $currentAppVersion (بناء 5)",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1E293B),
                            contentColor = Color(0xFF38BDF8)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isCheckingUpdate) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("جاري الفحص...", fontSize = 12.sp)
                        } else {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("التحقق من التحديثات", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                updateCheckMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(text = msg, color = Color(0xFF38BDF8), fontSize = 12.sp)
                }

                // If Update Available Box
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
                                Text(
                                    text = "يتوفر إصدار جديد: ${info.versionName}",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp
                                )
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
                                    Text(
                                        text = "جاري تحميل التحديث: $downloadProgress%",
                                        color = InDriveNeonGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = InDriveNeonGreen,
                                        contentColor = Color(0xFF0F172A)
                                    ),
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

        Spacer(modifier = Modifier.height(20.dp))

        // =========================================================================
        // 2. GEOGRAPHIC ZONE PREFERENCES (المناطق المفضلة والمناطق السوداء)
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFF59E0B).copy(alpha = 0.5f))
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Section Title: Favorite Zones
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "المناطق المفضلة (تظهر باللون الذهبي)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "أي رحلة تنطلق أو تنتهي في هذه المناطق ستظهر بتمييز ذهبي وتوصية قبول فورية",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Favorite Zones Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    favoriteZones.forEach { zone ->
                        ZoneChip(
                            name = zone,
                            color = Color(0xFFF59E0B),
                            onDelete = { favoriteZones.remove(zone) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Add Favorite Zone Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newFavoriteText,
                        onValueChange = { newFavoriteText = it },
                        placeholder = { Text("أدخل اسم منطقة مفضلة (مثال: التجمع)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color(0xFFF59E0B),
                            unfocusedBorderColor = DarkCardBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val trimmed = newFavoriteText.trim()
                            if (trimmed.isNotEmpty() && !favoriteZones.contains(trimmed)) {
                                favoriteZones.add(trimmed)
                                newFavoriteText = ""
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = newFavoriteText.trim()
                            if (trimmed.isNotEmpty() && !favoriteZones.contains(trimmed)) {
                                favoriteZones.add(trimmed)
                                newFavoriteText = ""
                            }
                        },
                        modifier = Modifier
                            .background(Color(0xFFF59E0B), RoundedCornerShape(12.dp))
                            .size(50.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null, tint = Color(0xFF0F172A))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section Title: Blacklisted Zones
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "المنطقة السوداء / غير المرغوبة",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "تظهر بتحذير أحمر، ويتم رفضها تلقائياً إلا إذا كان سعر الرحلة مرتفعاً ومجزياً جداً",
                    color = TextSecondary,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Blacklist Zones Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    blacklistedZones.forEach { zone ->
                        ZoneChip(
                            name = zone,
                            color = Color(0xFFDC2626),
                            onDelete = { blacklistedZones.remove(zone) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Add Blacklist Zone Input
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newBlacklistText,
                        onValueChange = { newBlacklistText = it },
                        placeholder = { Text("أدخل منطقة غير مرغوبة (مثال: حلوان)", fontSize = 12.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = Color(0xFFDC2626),
                            unfocusedBorderColor = DarkCardBorder
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = {
                            val trimmed = newBlacklistText.trim()
                            if (trimmed.isNotEmpty() && !blacklistedZones.contains(trimmed)) {
                                blacklistedZones.add(trimmed)
                                newBlacklistText = ""
                            }
                        })
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            val trimmed = newBlacklistText.trim()
                            if (trimmed.isNotEmpty() && !blacklistedZones.contains(trimmed)) {
                                blacklistedZones.add(trimmed)
                                newBlacklistText = ""
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

        Spacer(modifier = Modifier.height(20.dp))

        // =========================================================================
        // 3. EGYPTIAN FUEL PRESETS
        // =========================================================================
        Text(
            text = "نوع الوقود المستخدم في مصر (تحديث سريع للسعر)",
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FuelChip("بنزين 92", "15.25", fuelPrice) { fuelPrice = "15.25" }
            FuelChip("بنزين 95", "17.00", fuelPrice) { fuelPrice = "17.00" }
            FuelChip("بنزين 80", "13.75", fuelPrice) { fuelPrice = "13.75" }
            FuelChip("غاز / سولار", "11.50", fuelPrice) { fuelPrice = "11.50" }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // =========================================================================
        // 4. VEHICLE & PRICING PARAMETERS
        // =========================================================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                SettingTextField(
                    label = "سعر لتر الوقود ($currency)",
                    value = fuelPrice,
                    onValueChange = { fuelPrice = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "معدل استهلاك الوقود (لتر لكل 100 كم)",
                    value = fuelConsumption,
                    onValueChange = { fuelConsumption = it },
                    hint = "المتوسط في المدن 8.0 - 9.5 لتر/100كم"
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "نسبة عمولة إن درايف (%)",
                    value = commission,
                    onValueChange = { commission = it },
                    hint = "المتوسط الحالي 9.5% - 10.5%"
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "تكلفة استهلاك وإهلاك السيارة ($currency لكل كم)",
                    value = wearTear,
                    onValueChange = { wearTear = it },
                    hint = "تشمل الكاوتش، تغيير الزيت، والصيانة الدورية (~1.2 ج.م/كم)"
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "الحد الأدنى المقبول لصافي الربح ($currency لكل كم)",
                    value = minProfitKm,
                    onValueChange = { minProfitKm = it },
                    hint = "أي رحلة تعطي أقل من هذا الرقم سيتم تحذيرك منها فوراً"
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "الهدف المالي اليومي المطلوب ($currency)",
                    value = dailyTarget,
                    onValueChange = { dailyTarget = it }
                )

                Spacer(modifier = Modifier.height(14.dp))

                SettingTextField(
                    label = "رمز العملة",
                    value = currency,
                    onValueChange = { currency = it },
                    keyboardType = KeyboardType.Text
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Save & Reset Buttons
        Button(
            onClick = {
                val newSettings = DriverSettings(
                    fuelConsumptionLPer100Km = fuelConsumption.toDoubleOrNull() ?: 8.5,
                    fuelPricePerLiter = fuelPrice.toDoubleOrNull() ?: 15.0,
                    inDriveCommissionPercent = commission.toDoubleOrNull() ?: 10.0,
                    wearAndTearPerKm = wearTear.toDoubleOrNull() ?: 1.2,
                    targetDailyEarnings = dailyTarget.toDoubleOrNull() ?: 1500.0,
                    minProfitPerKm = minProfitKm.toDoubleOrNull() ?: 8.0,
                    currency = currency.trim().ifEmpty { "EGP" },
                    favoriteZones = favoriteZones.toList(),
                    blacklistedZones = blacklistedZones.toList()
                )
                viewModel.updateSettings(newSettings)
                Toast.makeText(context, "تم حفظ وتطبيق معايير التسعير والمناطق بنجاح.", Toast.LENGTH_SHORT).show()
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
            Text("حفظ وتطبيق كافة الإعدادات والمناطق", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                val defaultSettings = DriverSettings()
                viewModel.updateSettings(defaultSettings)
                Toast.makeText(context, "تمت استعادة الإعدادات الافتراضية", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkCardBorder)
        ) {
            Text("استعادة القيم الافتراضية (Default)", color = TextSecondary, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
