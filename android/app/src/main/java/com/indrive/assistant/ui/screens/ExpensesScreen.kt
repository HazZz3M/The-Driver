package com.indrive.assistant.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
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
import com.indrive.assistant.ui.theme.DarkBackground
import com.indrive.assistant.ui.theme.DarkCardBorder
import com.indrive.assistant.ui.theme.DarkSurface
import com.indrive.assistant.ui.theme.DarkSurfaceVariant
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.theme.LossRed
import com.indrive.assistant.ui.theme.TextPrimary
import com.indrive.assistant.ui.theme.TextSecondary
import com.indrive.assistant.ui.theme.WarningYellow
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

    var showFuelDialog by remember { mutableStateOf(false) }
    var showOilDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
    ) {
        // Screen Header
        item {
            Text(
                text = "مصاريف الوقود والزيت ⛽🛢️",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary
            )
            Text(
                text = "حساب دقيق لمعدل استهلاك البنزين وتكلفة الزيت لكل كيلومتر",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Two Dedicated Action Buttons (تفويلة بنزين + غيار زيت)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { showFuelDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InDriveNeonGreen,
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.LocalGasStation, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "تفويلة بنزين ⛽", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Button(
                    onClick = { showOilDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF38BDF8),
                        contentColor = Color(0xFF0F172A)
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(imageVector = Icons.Default.Opacity, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "غيار زيت 🛢️", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Analytics Cards (Fuel Economy & True Cost/Km)
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
                        text = "مؤشرات أداء السيارة الفعلية (من واقع التفويلات)",
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
                            Text(text = "متوسط تكلفة الوقود/كم", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (avgCostPerKm != null) "${String.format("%.2f", avgCostPerKm)} ${settings.currency}/كم" else "بانتظار تفويلة",
                                color = InDriveNeonGreen,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Column {
                            Text(text = "معدل الاستهلاك الفعلي", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (avgConsumption != null) "${String.format("%.1f", avgConsumption)} لتر/100كم" else "بانتظار تفويلة",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }

                        Column {
                            Text(text = "إجمالي مصاريف اليوم", color = TextSecondary, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${totalExpenseAmount.toInt()} ${settings.currency}",
                                color = WarningYellow,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // List Header
        item {
            Text(
                text = "سجل التفويلات وغيارات الزيت (${expenses.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
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
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "⛽", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "لا توجد تفويلات أو غيارات زيت مسجلة حتى الآن",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "اضغط على 'تفويلة بنزين' أو 'غيار زيت' بالاعلى لحساب تكلفة الكيلومتر بدقة",
                            color = TextSecondary,
                            fontSize = 13.sp
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
                Spacer(modifier = Modifier.height(10.dp))
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // 1. FUEL REFILL DIALOG
    if (showFuelDialog) {
        FuelRefillDialog(
            currency = settings.currency,
            defaultPricePerLiter = settings.fuelPricePerLiter,
            onDismiss = { showFuelDialog = false },
            onConfirm = { liters, pricePerLiter, kmSinceLast, desc ->
                viewModel.addFuelRefill(liters, pricePerLiter, kmSinceLast, desc)
                showFuelDialog = false
                Toast.makeText(
                    context,
                    "تم تسجيل التفويلة وتحديث معدل استهلاك السيارة تلقائياً! ⛽",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }

    // 2. OIL CHANGE DIALOG
    if (showOilDialog) {
        OilChangeDialog(
            currency = settings.currency,
            onDismiss = { showOilDialog = false },
            onConfirm = { pricePerLiter, volumeMl, lifespanKm, desc ->
                viewModel.addOilChange(pricePerLiter, volumeMl, lifespanKm, desc)
                showOilDialog = false
                Toast.makeText(
                    context,
                    "تم تسجيل غيار الزيت وحساب تكلفته لكل كم بنجاح! 🛢️",
                    Toast.LENGTH_LONG
                ).show()
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                if (isFuel) InDriveNeonGreen.copy(alpha = 0.4f)
                else if (isOil) Color(0xFF38BDF8).copy(alpha = 0.4f)
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
                    Text(text = if (isFuel) "⛽" else if (isOil) "🛢️" else "🔧", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFuel) "تفويلة بنزين" else if (isOil) "غيار زيت موتور" else "مصروف صيانة",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${expense.amount.toInt()} $currency",
                        color = if (isFuel) InDriveNeonGreen else if (isOil) Color(0xFF38BDF8) else WarningYellow,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
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

// 1. FUEL REFILL DIALOG
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
                Text(text = "⛽", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "تسجيل تفويلة بنزين", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = litersText,
                    onValueChange = { litersText = it },
                    label = { Text("كم لتر اتحط في التانك (Liters)") },
                    placeholder = { Text("مثال: 35") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = InDriveNeonGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pricePerLiterText,
                    onValueChange = { pricePerLiterText = it },
                    label = { Text("سعر لتر البنزين ($currency)") },
                    placeholder = { Text("مثال: 15.25") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = InDriveNeonGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = kmSinceLastText,
                    onValueChange = { kmSinceLastText = it },
                    label = { Text("الكيلومترات المقطوعة من آخر تفويلة (كم)") },
                    placeholder = { Text("مثال: 410") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = InDriveNeonGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time calculation box
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
                                Text(text = "${String.format("%.1f", consumption)} لتر/100كم", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "سعر لتر/كم (التكلفة لكل كم):", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${String.format("%.2f", costPerKm)} $currency/كم", color = InDriveNeonGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("ملاحظات (مثال: محطة وطنية بنزين 92)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = InDriveNeonGreen,
                        unfocusedBorderColor = DarkCardBorder
                    )
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
                    contentColor = Color(0xFF0F172A)
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

// 2. OIL CHANGE DIALOG
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
                Text(text = "🛢️", fontSize = 22.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "تسجيل غيار زيت الموتور", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column {
                OutlinedTextField(
                    value = pricePerLiterText,
                    onValueChange = { pricePerLiterText = it },
                    label = { Text("سعر لتر الزيت ($currency)") },
                    placeholder = { Text("مثال: 320") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = volumeMlText,
                    onValueChange = { volumeMlText = it },
                    label = { Text("كم ملل اتحط (حجم الزيت)") },
                    placeholder = { Text("مثال: 3500 أو 4000 ملل") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = lifespanKmText,
                    onValueChange = { lifespanKmText = it },
                    label = { Text("الزيت نزل بعد كام كيلومتر (كم)") },
                    placeholder = { Text("مثال: 5000 أو 10000 كم") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkCardBorder
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time calculation box
                if (pricePerLiter > 0 && lifespanKm > 0) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkBackground),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF38BDF8))
                        )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "إجمالي تكلفة غيار الزيت:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${totalCost.toInt()} $currency", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "تكلفة الزيت لكل كيلومتر:", color = TextSecondary, fontSize = 12.sp)
                                Text(text = "${String.format("%.2f", costPerKm)} $currency/كم", color = InDriveNeonGreen, fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                OutlinedTextField(
                    value = descText,
                    onValueChange = { descText = it },
                    label = { Text("نوع الزيت وملاحظات (مثال: موبيل وان 5W-30 مع الفلتر)") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = Color(0xFF38BDF8),
                        unfocusedBorderColor = DarkCardBorder
                    )
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
                    containerColor = Color(0xFF38BDF8),
                    contentColor = Color(0xFF0F172A)
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
