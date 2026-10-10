package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indrive.assistant.ui.components.ProfitStatusBanner
import com.indrive.assistant.ui.components.QuickInputSlider
import com.indrive.assistant.ui.components.SmartBidOptionCard
import com.indrive.assistant.ui.theme.DarkBackground
import com.indrive.assistant.ui.theme.DarkCardBorder
import com.indrive.assistant.ui.theme.DarkSurface
import com.indrive.assistant.ui.theme.DarkSurfaceVariant
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.theme.TextPrimary
import com.indrive.assistant.ui.theme.TextSecondary
import com.indrive.assistant.ui.viewmodel.AssistantViewModel

@Composable
fun CalculatorScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.driverSettings.collectAsState()
    val pickupKm by viewModel.pickupDistanceKm.collectAsState()
    val tripKm by viewModel.tripDistanceKm.collectAsState()
    val fare by viewModel.offeredFare.collectAsState()
    val analysis by viewModel.fareAnalysis.collectAsState()
    val isShiftActive by viewModel.isShiftActive.collectAsState()

    var selectedBidFare by remember { mutableStateOf<Double?>(null) }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Screen Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "محلل رحلات إن درايف ⚡",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "تقييم العرض وحساب الأرباح في ثوانٍ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }

            // Shift indicator chip
            Surface(
                color = if (isShiftActive) InDriveNeonGreen.copy(alpha = 0.15f) else DarkSurfaceVariant,
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isShiftActive) InDriveNeonGreen else DarkCardBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(if (isShiftActive) InDriveNeonGreen else TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isShiftActive) "الوردية جارية" else "وردية متوقفة",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isShiftActive) InDriveNeonGreen else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Live Profitability Evaluation Banner
        ProfitStatusBanner(
            analysis = analysis,
            currency = settings.currency
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2. Sliders & Fast Inputs
        Text(
            text = "تفاصيل الطلب المعروض",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )

        Spacer(modifier = Modifier.height(8.dp))

        QuickInputSlider(
            title = "مسافة الوصول للراكب (Pickup)",
            value = pickupKm,
            unit = "كم",
            range = 0.2f..15f,
            step = 0.5,
            onValueChange = { viewModel.setPickupDistance(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        QuickInputSlider(
            title = "مسافة الرحلة (Trip Distance)",
            value = tripKm,
            unit = "كم",
            range = 1f..60f,
            step = 1.0,
            onValueChange = { viewModel.setTripDistance(it) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        QuickInputSlider(
            title = "عرض الراكب الحالي (Passenger Offer)",
            value = fare,
            unit = settings.currency,
            range = 10f..400f,
            step = 5.0,
            onValueChange = {
                viewModel.setOfferedFare(it)
                selectedBidFare = null
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Operational Costs Breakdown Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "تفاصيل التكاليف التشغيلية (Total: ${String.format("%.1f", analysis.totalDistanceKm)} كم)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(12.dp))

                CostItemRow(
                    label = "استهلاك البنزين (${String.format("%.2f", analysis.fuelLitersUsed)} لتر)",
                    amount = "${String.format("%.1f", analysis.fuelCost)} ${settings.currency}"
                )
                CostItemRow(
                    label = "عمولة إن درايف (${settings.inDriveCommissionPercent}%)",
                    amount = "${String.format("%.1f", analysis.commissionCost)} ${settings.currency}"
                )
                CostItemRow(
                    label = "استهلاك وصيانة السيارة",
                    amount = "${String.format("%.1f", analysis.wearAndTearCost)} ${settings.currency}"
                )

                Divider(
                    color = DarkCardBorder,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "إجمالي التكلفة الحقيقية:",
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${String.format("%.1f", analysis.totalOperatingCost)} ${settings.currency}",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 4. Smart Counter-Bids Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "خيارات المضاربة الذكية (Counter-Offers) 🎯",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "اختر سعراً للمزايدة",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        analysis.smartBids.forEach { bid ->
            val isSelected = selectedBidFare == bid.suggestedFare
            SmartBidOptionCard(
                bid = bid,
                currency = settings.currency,
                isSelected = isSelected,
                onSelect = {
                    selectedBidFare = bid.suggestedFare
                },
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Action Buttons (Accept vs Bid Counter-Offer)
        val targetFareToSave = selectedBidFare ?: fare

        Button(
            onClick = {
                viewModel.saveCurrentRide(targetFareToSave)
                Toast.makeText(
                    context,
                    "تم حفظ الرحلة (${targetFareToSave.toInt()} ${settings.currency}) في سجل الوردية اليومي! 🚀",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = InDriveNeonGreen,
                contentColor = Color(0xFF0F172A)
            )
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (selectedBidFare != null) "قبول وحفظ المزايدة (${targetFareToSave.toInt()} ${settings.currency})" else "قبول وحفظ رحلة الراكب (${targetFareToSave.toInt()} ${settings.currency})",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(
            onClick = {
                // Apply suggested recommended bid directly to slider
                viewModel.setOfferedFare(analysis.recommendedFare)
                selectedBidFare = analysis.recommendedFare
                Toast.makeText(
                    context,
                    "تم تطبيق السعر المقترح: ${analysis.recommendedFare.toInt()} ${settings.currency}",
                    Toast.LENGTH_SHORT
                ).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, DarkCardBorder)
        ) {
            Icon(imageVector = Icons.Default.Bolt, contentDescription = null, tint = InDriveNeonGreen)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "تطبيق السعر الموصى به (${analysis.recommendedFare.toInt()} ${settings.currency})",
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(30.dp))
    }
}

@Composable
fun CostItemRow(label: String, amount: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = amount, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
