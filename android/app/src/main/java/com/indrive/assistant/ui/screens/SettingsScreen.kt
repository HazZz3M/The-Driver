package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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

@Composable
fun SettingsScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSettings by viewModel.driverSettings.collectAsState()

    var fuelConsumption by remember(currentSettings) { mutableStateOf(currentSettings.fuelConsumptionLPer100Km.toString()) }
    var fuelPrice by remember(currentSettings) { mutableStateOf(currentSettings.fuelPricePerLiter.toString()) }
    var commission by remember(currentSettings) { mutableStateOf(currentSettings.inDriveCommissionPercent.toString()) }
    var wearTear by remember(currentSettings) { mutableStateOf(currentSettings.wearAndTearPerKm.toString()) }
    var dailyTarget by remember(currentSettings) { mutableStateOf(currentSettings.targetDailyEarnings.toString()) }
    var minProfitKm by remember(currentSettings) { mutableStateOf(currentSettings.minProfitPerKm.toString()) }
    var currency by remember(currentSettings) { mutableStateOf(currentSettings.currency) }

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
            text = "إعدادات السيارة والتسعير ⚙️",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextPrimary
        )
        Text(
            text = "تخصيص أسعار الوقود ونسب العمولات لحساب الأرباح بدقة",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Egyptian Fuel Presets
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

        // Settings Form Card
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
                    currency = currency.trim().ifEmpty { "EGP" }
                )
                viewModel.updateSettings(newSettings)
                Toast.makeText(context, "تم حفظ الإعدادات وتحديث محرك الحساب بنجاح! 💾", Toast.LENGTH_SHORT).show()
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
            Text("حفظ وتطبيق الإعدادات", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
