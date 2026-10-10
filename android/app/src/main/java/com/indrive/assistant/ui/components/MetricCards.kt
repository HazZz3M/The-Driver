package com.indrive.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.indrive.assistant.engine.FareAnalysisResult
import com.indrive.assistant.engine.ProfitabilityLevel
import com.indrive.assistant.engine.SmartBidOption
import com.indrive.assistant.ui.theme.DarkCardBorder
import com.indrive.assistant.ui.theme.DarkSurface
import com.indrive.assistant.ui.theme.DarkSurfaceVariant
import com.indrive.assistant.ui.theme.InDriveNeonGreen
import com.indrive.assistant.ui.theme.LossRed
import com.indrive.assistant.ui.theme.ProfitGreen
import com.indrive.assistant.ui.theme.TextPrimary
import com.indrive.assistant.ui.theme.TextSecondary
import com.indrive.assistant.ui.theme.WarningYellow

@Composable
fun ProfitStatusBanner(
    analysis: FareAnalysisResult,
    currency: String,
    modifier: Modifier = Modifier
) {
    val (bannerBg, bannerBorder, statusColor, icon) = when (analysis.profitabilityLevel) {
        ProfitabilityLevel.LOSS_DANGER -> Quadruple(
            Color(0x33EF4444),
            LossRed,
            LossRed,
            Icons.Default.Warning
        )
        ProfitabilityLevel.POOR_MARGIN -> Quadruple(
            Color(0x22FACC15),
            WarningYellow,
            WarningYellow,
            Icons.Default.Warning
        )
        ProfitabilityLevel.EXCELLENT_PROFIT -> Quadruple(
            Color(0x3300EA80),
            InDriveNeonGreen,
            InDriveNeonGreen,
            Icons.Default.CheckCircle
        )
        ProfitabilityLevel.GOOD_PROFIT -> Quadruple(
            Color(0x2210B981),
            ProfitGreen,
            ProfitGreen,
            Icons.Default.CheckCircle
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(bannerBg)
            .border(2.dp, bannerBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = analysis.statusMessage,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                Text(
                    text = "${if (analysis.netProfit >= 0) "+" else ""}${String.format("%.1f", analysis.netProfit)} $currency",
                    color = statusColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp
                )
            }

            analysis.warningNote?.let { note ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Sub-metrics row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SubMetricPill(
                    label = "صافي/كم",
                    value = "${String.format("%.1f", analysis.profitPerKm)} $currency"
                )
                SubMetricPill(
                    label = "تكلفة الوقود",
                    value = "${String.format("%.1f", analysis.fuelCost)} $currency"
                )
                SubMetricPill(
                    label = "عمولة إن درايف",
                    value = "${String.format("%.1f", analysis.commissionCost)} $currency"
                )
            }
        }
    }
}

@Composable
fun SubMetricPill(label: String, value: String) {
    Surface(
        color = DarkSurfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                color = TextSecondary,
                fontSize = 11.sp
            )
            Text(
                text = value,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun SmartBidOptionCard(
    bid: SmartBidOption,
    currency: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) InDriveNeonGreen else DarkCardBorder
    val bgColor = if (isSelected) Color(0x1F00EA80) else DarkSurface

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable { onSelect() },
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bid.title,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = if (bid.tag == "OPTIMAL") InDriveNeonGreen.copy(alpha = 0.2f) else DarkSurfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = bid.tag,
                            color = if (bid.tag == "OPTIMAL") InDriveNeonGreen else TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bid.description,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "صافي ربح: +${String.format("%.1f", bid.netProfit)} $currency (${String.format("%.1f", bid.profitPerKm)}/كم)",
                    color = InDriveNeonGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${bid.suggestedFare.toInt()}",
                    color = if (isSelected) InDriveNeonGreen else TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = currency,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun QuickInputSlider(
    title: String,
    value: Double,
    unit: String,
    range: ClosedFloatingPointRange<Float>,
    step: Double = 1.0,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.linearGradient(listOf(DarkCardBorder, DarkCardBorder)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onValueChange((value - step).coerceAtLeast(range.start.toDouble())) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    ) {
                        Text(text = "−", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = "${String.format("%.1f", value)} $unit",
                        color = InDriveNeonGreen,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )

                    IconButton(
                        onClick = { onValueChange((value + step).coerceAtMost(range.endInclusive.toDouble())) },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceVariant)
                    ) {
                        Text(text = "+", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Slider(
                value = value.toFloat(),
                onValueChange = { onValueChange(it.toDouble()) },
                valueRange = range,
                colors = SliderDefaults.colors(
                    thumbColor = InDriveNeonGreen,
                    activeTrackColor = InDriveNeonGreen,
                    inactiveTrackColor = DarkSurfaceVariant
                )
            )
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
