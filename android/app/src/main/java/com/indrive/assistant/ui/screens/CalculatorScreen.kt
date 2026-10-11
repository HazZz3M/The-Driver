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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
    val vehicleProfiles by viewModel.vehicleProfiles.collectAsState()
    val activeVehicleId by viewModel.activeVehicleId.collectAsState()

    var selectedBidFare by remember { mutableStateOf<Double?>(null) }
    var showVehicleMenu by remember { mutableStateOf(false) }

    val activeProfile = vehicleProfiles.firstOrNull { it.id == activeVehicleId } ?: vehicleProfiles.firstOrNull()

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
                    text = "محلل عروض إن درايف والتسعير",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
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

        Spacer(modifier = Modifier.height(12.dp))

        // Active Vehicle Quick Switcher Strip
        if (activeProfile != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showVehicleMenu = true },
                color = DarkSurface,
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = InDriveNeonGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "المركبة النشطة: ${activeProfile.name}",
                            color = TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "(${activeProfile.fuelConsumptionLPer100Km}L / ${activeProfile.fuelPricePerLiter} ج.م)",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null, tint = TextSecondary)
                }

                DropdownMenu(
                    expanded = showVehicleMenu,
                    onDismissRequest = { showVehicleMenu = false }
                ) {
                    vehicleProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "${profile.name} (${profile.fuelType})",
                                    fontWeight = if (profile.id == activeVehicleId) FontWeight.Bold else FontWeight.Normal,
                                    color = if (profile.id == activeVehicleId) InDriveNeonGreen else TextPrimary
                                )
                            },
                            onClick = {
                                viewModel.switchActiveVehicle(profile)
                                showVehicleMenu = false
                                Toast.makeText(context, "تم التبديل إلى '${profile.name}'", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

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

        Spacer(modifier = Modifier.height(6.dp))

        // Fast Fare Delta Increments
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(-10.0, -5.0, 5.0, 10.0, 15.0, 20.0).forEach { delta ->
                val label = if (delta > 0) "+${delta.toInt()}" else "${delta.toInt()}"
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            val next = (fare + delta).coerceIn(10.0, 600.0)
                            viewModel.setOfferedFare(next)
                            selectedBidFare = null
                        },
                    color = DarkSurfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$label ${settings.currency}",
                            color = if (delta > 0) InDriveNeonGreen else Color(0xFFEF4444),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2.5 Quick Zone Evaluator & Tagger with (!) badge
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkCardBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFFB800),
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("!", color = Color(0xFF070B14), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "فحص وتصنيف المنطقة (!)",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 13.5.sp
                        )
                    }
                    Text(
                        text = "اضغط (!) لإضافة المنطقة",
                        color = TextSecondary,
                        fontSize = 10.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                var zoneInputText by remember { mutableStateOf("") }
                val isFav: Boolean = remember(zoneInputText, settings) { settings.isFavorite(zoneInputText) }
                val isBlack: Boolean = remember(zoneInputText, settings) { settings.isBlacklisted(zoneInputText) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = zoneInputText,
                        onValueChange = { zoneInputText = it },
                        placeholder = { Text("اكتب اسم منطقة لفحصها أو تصنيفها...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (zoneInputText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (isFav) {
                                    viewModel.removeFavoriteZone(zoneInputText)
                                    Toast.makeText(context, "تمت إزالة '$zoneInputText' من المفضلة", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addFavoriteZone(zoneInputText)
                                    Toast.makeText(context, "تمت إضافة '$zoneInputText' للمفضلة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isFav) Color(0xFF475569) else Color(0xFFFFB800),
                                contentColor = Color(0xFF070B14)
                            )
                        ) {
                            Text(if (isFav) "مفضلة" else "+ مفضلة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Button(
                            onClick = {
                                if (isBlack) {
                                    viewModel.removeBlacklistZone(zoneInputText)
                                    Toast.makeText(context, "تم إلغاء حظر '$zoneInputText'", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.addBlacklistZone(zoneInputText)
                                    Toast.makeText(context, "تمت إضافة '$zoneInputText' للمحظورة", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isBlack) Color(0xFF475569) else Color(0xFFFF3355),
                                contentColor = Color.White
                            )
                        ) {
                            Text(if (isBlack) "محظورة" else "+ محظورة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

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
                text = "خيارات التسعير والمزايدة الذكية (Counter-Bids)",
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
                    "تم تسجيل الرحلة (${targetFareToSave.toInt()} ${settings.currency}) في سجل الوردية بنجاح.",
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
