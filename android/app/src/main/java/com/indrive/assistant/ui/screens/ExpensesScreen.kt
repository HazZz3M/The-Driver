package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.indrive.assistant.data.model.Expense
import com.indrive.assistant.data.model.VehicleProfile
import com.indrive.assistant.ui.theme.AccentAmber
import com.indrive.assistant.ui.theme.AccentCyan
import com.indrive.assistant.ui.theme.AccentPurple
import com.indrive.assistant.ui.theme.DarkBackground
import com.indrive.assistant.ui.theme.DarkCardBorder
import com.indrive.assistant.ui.theme.DarkSurface
import com.indrive.assistant.ui.theme.DarkSurfaceVariant
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.theme.LossRed
import com.indrive.assistant.ui.theme.TextPrimary
import com.indrive.assistant.ui.theme.TextSecondary
import com.indrive.assistant.ui.viewmodel.AssistantViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ExpensesScreen(
    viewModel: AssistantViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.driverSettings.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val totalExpenseAmount by viewModel.totalExpensesAmount.collectAsState()
    val avgConsumption by viewModel.averageFuelConsumption.collectAsState()
    val avgCostPerKm by viewModel.averageFuelCostPerKm.collectAsState()

    val vehicleProfiles by viewModel.vehicleProfiles.collectAsState()
    val activeVehicleId by viewModel.activeVehicleId.collectAsState()
    val activeProfile = vehicleProfiles.firstOrNull { it.id == activeVehicleId } ?: vehicleProfiles.firstOrNull()

    var showFuelDialog by remember { mutableStateOf(false) }
    var showOilDialog by remember { mutableStateOf(false) }
    var showSparePartDialog by remember { mutableStateOf(false) }
    var showAddVehicleDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Header
        item {
            Column {
                Text(
                    text = "المركبات والمصاريف التشغيلية",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = "تخصيص استهلاكات أسطول المركبات وحساب تكلفة الكيلومتر والوقود والزيوت",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }

        // 1. ACTIVE VEHICLE PROFILE HUB CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(InDriveNeonGreen.copy(alpha = 0.6f))
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = InDriveNeonGreen.copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsCar,
                                        contentDescription = null,
                                        tint = InDriveNeonGreen,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = activeProfile?.name ?: "المركبة النشطة",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TextPrimary,
                                    fontSize = 17.sp
                                )
                                Text(
                                    text = "${activeProfile?.fuelType ?: "بنزين 92"} • موديل ${activeProfile?.modelYear?.ifBlank { "2021" } ?: ""}",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = { showAddVehicleDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkSurfaceVariant,
                                contentColor = InDriveNeonGreen
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ مركبة", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Quick Switcher Chips Row
                    Text(
                        text = "التبديل السريع بين مركبات الأسطول:",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(vehicleProfiles, key = { it.id }) { prof ->
                            val isCurrent = prof.id == activeVehicleId
                            FilterChip(
                                selected = isCurrent,
                                onClick = {
                                    viewModel.switchActiveVehicle(prof)
                                    Toast.makeText(context, "تم تفعيل '${prof.name}' وتطبيق معدلات استهلاكها فوراً", Toast.LENGTH_SHORT).show()
                                },
                                label = {
                                    Text(
                                        text = prof.name,
                                        fontSize = 11.5.sp,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (isCurrent) {
                                    { Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = InDriveNeonGreen,
                                    selectedLabelColor = Color(0xFF070B14),
                                    containerColor = DarkSurfaceVariant,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Vehicle Active Metrics Grid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("الاستهلاك المعتمد", color = TextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${activeProfile?.fuelConsumptionLPer100Km ?: 8.5}L/100كم",
                                    color = AccentCyan,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("سعر لتر الوقود", color = TextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${activeProfile?.fuelPricePerLiter ?: 15.25} ج.م",
                                    color = InDriveNeonGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Surface(
                            modifier = Modifier.weight(1f),
                            color = DarkSurfaceVariant,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("إهلاك السيارة", color = TextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${activeProfile?.wearAndTearPerKm ?: 1.2} ج/كم",
                                    color = AccentAmber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "معدلات هذه المركبة مطبقة تلقائياً على معادلات التسعير العادل ومحلل العروض.",
                        color = Color(0xFF64748B),
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 2. THREE DEDICATED ACTION BUTTONS
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { showFuelDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InDriveNeonGreen,
                        contentColor = Color(0xFF070B14)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocalGasStation, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "تفويلة", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { showOilDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentCyan,
                        contentColor = Color(0xFF070B14)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Opacity, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "غيار زيت", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                Button(
                    onClick = { showSparePartDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentPurple,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "قطع غيار", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // 3. ANALYTICS & ACTUAL PERFORMANCE CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(DarkCardBorder)
                )
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "مؤشرات الأداء الفعلي (من واقع التفويلات)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(text = "تكلفة الوقود الفعلي/كم", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (avgCostPerKm != null) "${String.format("%.2f", avgCostPerKm)} ${settings.currency}/كم" else "بانتظار تفويلة",
                                color = InDriveNeonGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }

                        Column {
                            Text(text = "الاستهلاك الفعلي المحسوب", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (avgConsumption != null) "${String.format("%.1f", avgConsumption)} لتر/100كم" else "بانتظار تفويلة",
                                color = AccentCyan,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }

                        Column {
                            Text(text = "إجمالي مصاريف اليوم", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${totalExpenseAmount.toInt()} ${settings.currency}",
                                color = AccentAmber,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 17.sp
                            )
                        }
                    }
                }
            }
        }

        // 4. EXPENSE RECORDS LIST
        item {
            Text(
                text = "سجل التفويلات وغيارات الزيت وقطع الغيار (${expenses.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        if (expenses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalGasStation,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد تفويلات أو مصاريف مسجلة حتى الآن",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "سجل تفويلاتك وقطع الغيار لحساب تكلفة الكيلومتر بدقة فائقة",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        } else {
            items(expenses, key = { it.id }) { expense ->
                DetailedExpenseItemCard(
                    expense = expense,
                    currency = settings.currency,
                    onDelete = { viewModel.deleteExpense(expense.id) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // DIALOGS
    if (showFuelDialog) {
        FuelRefillDialog(
            currency = settings.currency,
            defaultPricePerLiter = activeProfile?.fuelPricePerLiter ?: settings.fuelPricePerLiter,
            onDismiss = { showFuelDialog = false },
            onConfirm = { liters, pricePerLiter, kmSinceLast, desc ->
                viewModel.addFuelRefill(liters, pricePerLiter, kmSinceLast, desc)
                showFuelDialog = false
                Toast.makeText(context, "تم تسجيل التفويلة وتحديث استهلاك السيارة بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showOilDialog) {
        OilChangeDialog(
            currency = settings.currency,
            onDismiss = { showOilDialog = false },
            onConfirm = { pricePerLiter, volumeMl, lifespanKm, desc ->
                viewModel.addOilChange(pricePerLiter, volumeMl, lifespanKm, desc)
                showOilDialog = false
                Toast.makeText(context, "تم تسجيل غيار الزيت بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showSparePartDialog) {
        SparePartDialog(
            currency = settings.currency,
            onDismiss = { showSparePartDialog = false },
            onConfirm = { partName, amount, lifespanKm, updateWearTear, desc ->
                viewModel.addSparePart(partName, amount, lifespanKm, updateWearTear, desc)
                showSparePartDialog = false
                Toast.makeText(
                    context,
                    if (updateWearTear) "تم تسجيل قطعة الغيار وتحديث معدل إهلاك السيارة بنجاح" else "تم تسجيل قطعة الغيار بنجاح",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }

    if (showAddVehicleDialog) {
        AddVehicleDialog(
            onDismiss = { showAddVehicleDialog = false },
            onConfirm = { newProfile: VehicleProfile ->
                val updated = vehicleProfiles.toMutableList().apply { add(newProfile) }
                viewModel.saveVehicleProfiles(updated)
                viewModel.switchActiveVehicle(newProfile)
                showAddVehicleDialog = false
                Toast.makeText(context, "تمت إضافة وتفعيل '${newProfile.name}' بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun DetailedExpenseItemCard(
    expense: Expense,
    currency: String,
    onDelete: () -> Unit
) {
    val timeFormat = SimpleDateFormat("yyyy/MM/dd - hh:mm a", Locale.getDefault())
    val formattedTime = timeFormat.format(Date(expense.timestamp))

    val isFuel = expense.category == "FUEL"
    val isOil = expense.category == "OIL_CHANGE"
    val isSparePart = expense.category == "SPARE_PART"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isFuel) InDriveNeonGreen.copy(alpha = 0.4f)
                else if (isOil) AccentCyan.copy(alpha = 0.4f)
                else if (isSparePart) AccentPurple.copy(alpha = 0.4f)
                else DarkCardBorder
            )
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isFuel) Icons.Default.LocalGasStation else if (isOil) Icons.Default.Opacity else Icons.Default.Build,
                        contentDescription = null,
                        tint = if (isFuel) InDriveNeonGreen else if (isOil) AccentCyan else if (isSparePart) AccentPurple else AccentAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFuel) "تفويلة بنزين" else if (isOil) "غيار زيت موتور" else if (isSparePart) (expense.partName ?: "قطعة غيار") else "مصروف صيانة",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${expense.amount.toInt()} $currency",
                        color = if (isFuel) InDriveNeonGreen else if (isOil) AccentCyan else if (isSparePart) AccentPurple else AccentAmber,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-metrics badges
            if (isFuel && expense.consumptionLPer100Km != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        label = "المسافة",
                        value = "${expense.kmSinceLastRefill?.toInt()} كم"
                    )
                    MetricBadge(
                        label = "الاستهلاك",
                        value = "${String.format("%.1f", expense.consumptionLPer100Km)} لتر/100كم"
                    )
                    MetricBadge(
                        label = "تكلفة/كم",
                        value = "${String.format("%.2f", expense.fuelCostPerKm)} $currency/كم"
                    )
                }
            } else if (isOil && expense.oilCostPerKm != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        label = "الكمية",
                        value = "${expense.oilVolumeMl} ملل"
                    )
                    MetricBadge(
                        label = "مسافة الزيت",
                        value = "${expense.oilLifespanKm?.toInt()} كم"
                    )
                    MetricBadge(
                        label = "تكلفة الزيت/كم",
                        value = "${String.format("%.2f", expense.oilCostPerKm)} $currency/كم"
                    )
                }
            } else if (isSparePart) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBadge(
                        label = "القطعة",
                        value = expense.partName ?: "صيانة"
                    )
                    if (expense.partLifespanKm != null && expense.partLifespanKm > 0) {
                        MetricBadge(
                            label = "عمر القطعة",
                            value = "${expense.partLifespanKm.toInt()} كم"
                        )
                        if (expense.partCostPerKm != null) {
                            MetricBadge(
                                label = "إهلاك/كم",
                                value = "${String.format("%.2f", expense.partCostPerKm)} $currency/كم"
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${expense.description} • $formattedTime",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun MetricBadge(label: String, value: String) {
    Surface(
        color = DarkSurfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "$label: ", color = TextSecondary, fontSize = 11.sp)
            Text(text = value, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

@Composable
fun FuelRefillDialog(
    currency: String,
    defaultPricePerLiter: Double,
    onDismiss: () -> Unit,
    onConfirm: (liters: Double, pricePerLiter: Double, kmSinceLast: Double, desc: String) -> Unit
) {
    var litersText by remember { mutableStateOf("") }
    var pricePerLiterText by remember { mutableStateOf(defaultPricePerLiter.toString()) }
    var kmSinceLastText by remember { mutableStateOf("") }
    var descText by remember { mutableStateOf("") }

    val liters = litersText.toDoubleOrNull() ?: 0.0
    val pricePerLiter = pricePerLiterText.toDoubleOrNull() ?: defaultPricePerLiter
    val kmSinceLast = kmSinceLastText.toDoubleOrNull() ?: 0.0

    val totalCost = liters * pricePerLiter
    val consumption = if (kmSinceLast > 0) (liters / kmSinceLast) * 100.0 else 0.0
    val costPerKm = if (kmSinceLast > 0) totalCost / kmSinceLast else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalGasStation,
                    contentDescription = null,
                    tint = InDriveNeonGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "تسجيل تفويلة بنزين", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("كم لتر اتحط في التانك (Liters)") },
                    placeholder = { Text("مثال: 35") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pricePerLiterText,
                    onValueChange = { pricePerLiterText = it },
                    label = { Text("سعر لتر البنزين ($currency)") },
                    placeholder = { Text("مثال: 15.25") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = kmSinceLastText,
                    onValueChange = { kmSinceLastText = it },
                    label = { Text("الكيلومترات المقطوعة من آخر تفويلة (كم)") },
                    placeholder = { Text("مثال: 410") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (liters > 0 && kmSinceLast > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(InDriveNeonGreen)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "إجمالي التكلفة:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${totalCost.toInt()} $currency", color = InDriveNeonGreen, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "معدل الاستهلاك المحسوب:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${String.format("%.1f", consumption)} لتر/100كم", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "التكلفة لكل كم:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${String.format("%.2f", costPerKm)} $currency/كم", color = InDriveNeonGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("ملاحظات (مثال: محطة وطنية)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (liters > 0 && kmSinceLast > 0) {
                        onConfirm(liters, pricePerLiter, kmSinceLast, descText)
                    }
                },
                enabled = liters > 0 && kmSinceLast > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = InDriveNeonGreen,
                    contentColor = Color(0xFF070B14)
                )
            ) {
                Text("حفظ وحساب التكلفة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
fun OilChangeDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (pricePerLiter: Double, volumeMl: Int, lifespanKm: Double, desc: String) -> Unit
) {
    var pricePerLiterText by remember { mutableStateOf("") }
    var volumeMlText by remember { mutableStateOf("4000") }
    var lifespanKmText by remember { mutableStateOf("5000") }
    var descText by remember { mutableStateOf("") }

    val pricePerLiter = pricePerLiterText.toDoubleOrNull() ?: 0.0
    val volumeMl = volumeMlText.toIntOrNull() ?: 4000
    val lifespanKm = lifespanKmText.toDoubleOrNull() ?: 5000.0

    val totalCost = (volumeMl / 1000.0) * pricePerLiter
    val costPerKm = if (lifespanKm > 0) totalCost / lifespanKm else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Opacity,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "تسجيل غيار زيت الموتور", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pricePerLiterText,
                    onValueChange = { pricePerLiterText = it },
                    label = { Text("سعر لتر الزيت ($currency)") },
                    placeholder = { Text("مثال: 320") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = volumeMlText,
                    onValueChange = { volumeMlText = it },
                    label = { Text("حجم الزيت (ملل)") },
                    placeholder = { Text("مثال: 3500 أو 4000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = lifespanKmText,
                    onValueChange = { lifespanKmText = it },
                    label = { Text("الزيت نزل بعد كام كيلومتر (كم)") },
                    placeholder = { Text("مثال: 5000 أو 10000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                if (pricePerLiter > 0 && lifespanKm > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(AccentCyan)
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "إجمالي تكلفة غيار الزيت:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${totalCost.toInt()} $currency", color = AccentCyan, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "تكلفة الزيت لكل كم:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${String.format("%.2f", costPerKm)} $currency/كم", color = InDriveNeonGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("نوع الزيت وملاحظات") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pricePerLiter > 0 && lifespanKm > 0) {
                        onConfirm(pricePerLiter, volumeMl, lifespanKm, descText)
                    }
                },
                enabled = pricePerLiter > 0 && lifespanKm > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCyan,
                    contentColor = Color(0xFF070B14)
                )
            ) {
                Text("حفظ غيار الزيت", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
fun SparePartDialog(
    currency: String,
    onDismiss: () -> Unit,
    onConfirm: (partName: String, amount: Double, lifespanKm: Double?, updateWearTear: Boolean, desc: String) -> Unit
) {
    var partNameText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    var hasKmWearTear by remember { mutableStateOf(true) }
    var lifespanKmText by remember { mutableStateOf("30000") }
    var updateWearTear by remember { mutableStateOf(true) }
    var descText by remember { mutableStateOf("") }

    val amount = amountText.toDoubleOrNull() ?: 0.0
    val lifespanKm = if (hasKmWearTear) lifespanKmText.toDoubleOrNull() else null
    val costPerKm = if (lifespanKm != null && lifespanKm > 0) amount / lifespanKm else 0.0

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = null,
                    tint = AccentPurple,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "تسجيل قطع غيار وصيانة", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = partNameText,
                    onValueChange = { partNameText = it },
                    label = { Text("اسم قطعة الغيار أو الصيانة") },
                    placeholder = { Text("مثال: تيل فرامل، طقم كاوتش") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("التكلفة الإجمالية ($currency)") },
                    placeholder = { Text("مثال: 1200") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = hasKmWearTear,
                        onCheckedChange = { hasKmWearTear = it },
                        colors = CheckboxDefaults.colors(checkedColor = AccentPurple)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "القطعة مرتبطة بعدد كيلومترات معينة",
                        color = TextPrimary,
                        fontSize = 12.sp
                    )
                }

                if (hasKmWearTear) {
                    OutlinedTextField(
                        value = lifespanKmText,
                        onValueChange = { lifespanKmText = it },
                        label = { Text("عمر القطعة بالكيلومتر (كم)") },
                        placeholder = { Text("مثال: 30000 أو 50000") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (amount > 0 && (lifespanKm ?: 0.0) > 0) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = DarkBackground),
                            border = CardDefaults.outlinedCardBorder().copy(
                                brush = androidx.compose.ui.graphics.SolidColor(AccentPurple)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "معدل إهلاك القطعة/كم:", color = TextSecondary, fontSize = 12.sp)
                                    Text(
                                        text = "${String.format("%.2f", costPerKm)} $currency/كم",
                                        color = AccentPurple,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = updateWearTear,
                                onCheckedChange = { updateWearTear = it },
                                colors = CheckboxDefaults.colors(checkedColor = InDriveNeonGreen)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "تحديث معدل إهلاك السيارة في التسعير تلقائياً",
                                color = InDriveNeonGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("ملاحظات إضافية") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (partNameText.isNotBlank() && amount > 0) {
                        onConfirm(partNameText.trim(), amount, lifespanKm, updateWearTear && hasKmWearTear, descText.trim())
                    }
                },
                enabled = partNameText.isNotBlank() && amount > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentPurple,
                    contentColor = Color.White
                )
            ) {
                Text("حفظ قطعة الغيار", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = TextSecondary)
            }
        }
    )
}

@Composable
fun AddVehicleDialog(
    onDismiss: () -> Unit,
    onConfirm: (VehicleProfile) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var modelYearText by remember { mutableStateOf("2021") }
    var fuelType by remember { mutableStateOf("بنزين 92") }
    var consumptionText by remember { mutableStateOf("8.5") }
    var fuelPriceText by remember { mutableStateOf("15.25") }
    var wearTearText by remember { mutableStateOf("1.2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = com.indrive.assistant.ui.theme.InDriveNeonGreen,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "إضافة وتخصيص مركبة جديدة", fontWeight = FontWeight.Bold, color = com.indrive.assistant.ui.theme.TextPrimary)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("اسم المركبة أو الطراز") },
                    placeholder = { Text("مثال: تويوتا كورولا 2021") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("بنزين 92", "بنزين 95", "بنزين 80", "غاز طبيعي").forEach { type ->
                        FilterChip(
                            selected = fuelType == type,
                            onClick = {
                                fuelType = type
                                fuelPriceText = when (type) {
                                    "بنزين 95" -> "17.00"
                                    "بنزين 92" -> "15.25"
                                    "بنزين 80" -> "13.75"
                                    "غاز طبيعي" -> "7.00"
                                    else -> "15.25"
                                }
                            },
                            label = { Text(type, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.indrive.assistant.ui.theme.InDriveNeonGreen,
                                selectedLabelColor = Color(0xFF070B14)
                            )
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = consumptionText,
                        onValueChange = { consumptionText = it },
                        label = { Text("الاستهلاك (لتر/100كم)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = fuelPriceText,
                        onValueChange = { fuelPriceText = it },
                        label = { Text("سعر اللتر (ج.م)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = wearTearText,
                    onValueChange = { wearTearText = it },
                    label = { Text("معدل الإهلاك لكل كم (ج.م)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameText.isNotBlank()) {
                        val profile = VehicleProfile(
                            name = nameText.trim(),
                            modelYear = modelYearText.trim(),
                            fuelType = fuelType,
                            fuelConsumptionLPer100Km = consumptionText.toDoubleOrNull() ?: 8.5,
                            fuelPricePerLiter = fuelPriceText.toDoubleOrNull() ?: 15.25,
                            wearAndTearPerKm = wearTearText.toDoubleOrNull() ?: 1.2
                        )
                        onConfirm(profile)
                    }
                },
                enabled = nameText.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = com.indrive.assistant.ui.theme.InDriveNeonGreen,
                    contentColor = Color(0xFF070B14)
                )
            ) {
                Text("إضافة وحفظ المركبة", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = com.indrive.assistant.ui.theme.TextSecondary)
            }
        }
    )
}

