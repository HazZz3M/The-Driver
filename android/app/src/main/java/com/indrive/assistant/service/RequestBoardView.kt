package com.indrive.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.data.model.HudPrefs
import com.indrive.assistant.engine.BidAdvisor
import com.indrive.assistant.engine.FareCalculatorEngine
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The requests board: one card per request read from inDrive, newest first, each with a
 * snapshot of inDrive's own map of the trip, the price and the verdict, and the driver's controls:
 * accept, hide, the bid buttons (best one ★), and their own price (− / + then send).
 * Used by the floating window and by LiveMapActivity.
 *
 * Main thread only. Call [start] when shown and [stop] when hidden.
 */
@SuppressLint("ViewConstructor")
class RequestBoardView(context: Context, private val compact: Boolean) : FrameLayout(context) {

    private val list = LinearLayout(context)
    private val scroll = ScrollView(context)
    private val empty = TextView(context)
    private val handler = Handler(Looper.getMainLooper())

    // What the driver has open on a card (survives redraws).
    private var customKey: String? = null
    private var customValue = 0
    private var confirmKey: String? = null
    private var confirmUntil = 0L
    private var bigImageKey: String? = null

    private var running = false
    private var lastSig = ""

    private val listener: () -> Unit = { refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        setBackgroundColor(Color.parseColor("#0F172A"))
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(8), dp(8), dp(8), dp(8))
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        empty.apply {
            text = "مفيش طلبات لسه.\nافتح قائمة الطلبات في إن درايف وهتظهر هنا."
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }
        addView(empty, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    fun start() {
        if (running) return
        running = true
        lastSig = ""
        LiveRequests.addListener(listener)
        handler.post(ticker)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(ticker)
        LiveRequests.removeListener(listener)
    }

    fun destroy() = stop()

    private fun prefs(): HudPrefs = InDriveAssistantApp.instance.repository.getHudPrefs()

    // =========================================================================
    // DRAWING
    // =========================================================================

    private fun refresh() {
        if (!running) return
        val now = System.currentTimeMillis()
        val items = LiveRequests.snapshot(prefs().liveMapKeepSeconds * 1000L)
            .filter { it.status != LiveRequests.STATUS_HIDDEN }
        if (confirmKey != null && now >= confirmUntil) confirmKey = null

        // Redraw only when something visible changed: rebuilding the buttons under the driver's
        // finger would swallow the tap.
        val sig = items.joinToString(";") {
            "${it.key}|${it.status}|${it.onScreen}|${it.card.fare}|${it.level}|${it.card.bidChips.map { b -> b.value }}|" +
                "${it.analysis.netProfit.roundToInt()}|${it.card.tripKnown}|${System.identityHashCode(LiveRequests.snapshotOf(it.key))}"
        } + "#$customKey:$customValue#$confirmKey#$bigImageKey#${LiveRequests.openKey}"
        if (sig == lastSig) return
        lastSig = sig

        list.removeAllViews()
        empty.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        for (e in items) list.addView(card(e))
    }

    private fun card(e: LiveRequests.Entry): View {
        val c = e.card
        val a = e.analysis
        val color = levelColor(e.level)
        val live = e.status == LiveRequests.STATUS_LIVE
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(2), if (e.onScreen) color else Color.parseColor("#334155"))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            alpha = if (e.onScreen || !live) 1f else 0.6f
        }
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(8)
        box.layoutParams = lp

        // Top: inDrive's map of the trip, and the numbers next to it.
        val big = bigImageKey == e.key
        val top = LinearLayout(context).apply { orientation = if (big) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL }
        top.addView(mapImage(e, big), if (big) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 220 else 300))
        } else {
            LinearLayout.LayoutParams(0, dp(if (compact) 112 else 140), 0.46f).apply { marginEnd = dp(10) }
        })

        val info = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val approx = if (c.distanceEstimated) "≈" else ""
        info.addView(row(
            text("${c.fare.roundToInt()} ج.م", 19f, Color.WHITE, bold = true),
            text("  ${LEVEL_LABELS[e.level]}", 13f, color, bold = true)
        ))
        info.addView(text("صافي $approx${a.netProfit.roundToInt()} • ${fmt(a.profitPerKm)}/كم", 13f, color, bold = true))
        info.addView(text(
            "الوصول ${if (c.pickupKnown) fmt(c.pickupDistanceKm) else "؟"} • المشوار ${if (c.tripKnown) fmt(c.tripDistanceKm) else "؟"} كم",
            12f, Color.parseColor("#CBD5E1")
        ))
        val who = listOf(c.passengerName, c.passengerRating.takeIf { it.isNotBlank() }?.let { "★$it" })
            .filterNotNull().filter { it.isNotBlank() }.joinToString(" ")
        if (who.isNotBlank()) info.addView(text(who, 12f, Color.parseColor("#CBD5E1"), lines = 1))
        info.addView(text("← ${place(c.dropoffAddress)}", 12f, Color.WHITE, lines = if (big) 2 else 1))
        info.addView(text(stateLine(e), 11f, Color.parseColor("#94A3B8"), lines = 1))
        top.addView(info, if (big) {
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) }
        } else {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.54f)
        })
        box.addView(top)
        if (big) {
            box.addView(text("من: ${c.pickupAddress.ifBlank { "—" }}", 12f, Color.parseColor("#CBD5E1")))
            box.addView(text("إلى: ${c.dropoffAddress.ifBlank { "—" }}", 12f, Color.parseColor("#CBD5E1")))
        }
        if (!live) return box

        // Accept / hide.
        val now = System.currentTimeMillis()
        val confirming = confirmKey == e.key && now < confirmUntil
        box.addView(row(
            button(if (confirming) "اضغط تاني للتأكيد" else "قبول ${c.fare.roundToInt()}", COLOR_ACCEPT, weight = 1.4f) { accept(e.key) },
            button("إخفاء", COLOR_HIDE) { act(e.key, InDriveAccessibilityService.MAP_HIDE) },
            topMargin = 8
        ))

        // Bid buttons (once the request was opened) and the driver's own price.
        val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
        val bids = mutableListOf<View>()
        if (c.bidChips.isNotEmpty()) {
            val rated = BidAdvisor.rate(c, settings)
            val best = BidAdvisor.best(rated)?.chip?.value
            for (r in rated) {
                val v = r.chip.value
                bids.add(button((if (v == best) "★ " else "") + "$v\nصافي ${r.analysis.netProfit.roundToInt()}",
                    if (v == best) COLOR_BEST else COLOR_BID) { act(e.key, InDriveAccessibilityService.MAP_BID, v) })
            }
        }
        val customOpen = customKey == e.key
        bids.add(button(if (customOpen) "إلغاء" else "سعر تاني", COLOR_CUSTOM) {
            if (customOpen) customKey = null else {
                customKey = e.key
                customValue = startPrice(e)
            }
            lastSig = ""
            refresh()
        })
        box.addView(row(*bids.toTypedArray(), topMargin = 6))

        if (customOpen) {
            val net = FareCalculatorEngine.analyze(
                pickupDistanceKm = c.pickupDistanceKm,
                tripDistanceKm = c.tripDistanceKm,
                estimatedMinutes = FareCalculatorEngine.estimateMinutes(c.totalDistanceKm),
                offeredFare = customValue.toDouble(),
                settings = settings,
                pickupAddress = c.pickupAddress,
                dropoffAddress = c.dropoffAddress,
                passengerName = c.passengerName
            ).netProfit.roundToInt()
            fun step(d: Int) = button(if (d > 0) "+$d" else "$d", COLOR_BID) {
                customValue = (customValue + d).coerceIn(5, 5000)
                lastSig = ""
                refresh()
            }
            box.addView(row(
                step(-10), step(-5),
                text("$customValue\nصافي $net", 14f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER },
                step(5), step(10),
                topMargin = 6
            ))
            box.addView(row(
                button("ابعت $customValue ج.م", COLOR_ACCEPT) {
                    customKey = null
                    act(e.key, InDriveAccessibilityService.MAP_CUSTOM, customValue)
                },
                topMargin = 6
            ))
        }
        return box
    }

    /** inDrive's map of this trip, or a placeholder until it is taken. Tap: bigger / smaller. */
    private fun mapImage(e: LiveRequests.Entry, big: Boolean): View {
        val bmp = LiveRequests.snapshotOf(e.key)
        val view: View = if (bmp != null) {
            ImageView(context).apply {
                setImageBitmap(bmp)
                scaleType = if (big) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER_CROP
            }
        } else {
            TextView(context).apply {
                text = when {
                    !e.onScreen -> "مفيش صورة للمسار"
                    prefs().boardAutoOpen -> "بيتصوّر المسار…"
                    else -> "افتح الطلب عشان يتصوّر المسار"
                }
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 11f
                gravity = Gravity.CENTER
            }
        }
        view.background = GradientDrawable().apply {
            setColor(Color.parseColor("#0B1220"))
            cornerRadius = dp(10).toFloat()
        }
        view.clipToOutline = true
        view.setOnClickListener {
            bigImageKey = if (big) null else e.key
            refresh()
        }
        return view
    }

    private fun stateLine(e: LiveRequests.Entry): String {
        val age = ((System.currentTimeMillis() - e.firstSeen) / 1000).toInt()
        val ago = if (age < 60) "من $age ث" else "من ${age / 60} د"
        val where = when {
            e.status != LiveRequests.STATUS_LIVE -> e.statusDetail
            LiveRequests.openKey == e.key -> "مفتوح في إن درايف"
            e.onScreen -> "في إن درايف"
            else -> "مش ظاهر دلوقتي"
        }
        return "$where • $ago"
    }

    /** Where the driver's own price starts: the passenger's fare, or the target price if higher, rounded up to 5. */
    private fun startPrice(e: LiveRequests.Entry): Int {
        val target = maxOf(e.card.fare, e.analysis.recommendedFare)
        return (ceil(target / 5.0) * 5).toInt().coerceIn(5, 5000)
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    private fun accept(key: String) {
        if (prefs().liveMapConfirmAccept && !(confirmKey == key && System.currentTimeMillis() < confirmUntil)) {
            confirmKey = key
            confirmUntil = System.currentTimeMillis() + 3000
            refresh()
            return
        }
        confirmKey = null
        act(key, InDriveAccessibilityService.MAP_ACCEPT)
    }

    private fun act(key: String, action: String, value: Int = 0) {
        if (!InDriveAccessibilityService.mapAction(key, action, value)) {
            Toast.makeText(context, "شغّل قراءة الشاشة الأول", Toast.LENGTH_SHORT).show()
        }
        refresh()
    }

    // =========================================================================
    // SMALL VIEW HELPERS
    // =========================================================================

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun fmt(v: Double) = if (v >= 10) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)

    /** "Place (street، neighbourhood، …)" → "Place، neighbourhood" for one line. */
    private fun place(address: String): String {
        val open = address.indexOf('(')
        if (open < 0) return address.trim().ifBlank { "—" }
        val name = address.substring(0, open).trim()
        val inner = address.substring(open + 1).split('،', ',', ')').map { it.trim() }.firstOrNull { it.length > 2 }
        return listOfNotNull(name.ifBlank { null }, inner).joinToString("، ")
    }

    private fun row(vararg views: View, topMargin: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        if (topMargin > 0) setPadding(0, dp(topMargin), 0, 0)
        for (v in views) {
            val w = (v.tag as? Float) ?: if (v is TextView && v.background != null) 1f else 0f
            val lp = if (w > 0f) LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, w)
            else LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (views.size > 1) lp.marginEnd = dp(4)
            addView(v, lp)
        }
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 2) = TextView(context).apply {
        text = s
        textSize = size
        setTextColor(color)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, color: Int, weight: Float = 1f, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = if (compact) 12.5f else 13.5f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(10).toFloat()
        }
        // Big enough to hit quickly while driving.
        minHeight = dp(42)
        setPadding(dp(6), dp(6), dp(6), dp(6))
        tag = weight
        setOnClickListener { onClick() }
    }

    companion object {
        private val COLOR_ACCEPT = Color.parseColor("#16A34A")
        private val COLOR_HIDE = Color.parseColor("#B91C1C")
        private val COLOR_BID = Color.parseColor("#334155")
        private val COLOR_BEST = Color.parseColor("#15803D")
        private val COLOR_CUSTOM = Color.parseColor("#4F46E5")

        private val LEVEL_LABELS = mapOf(
            "EXCELLENT" to "ممتاز",
            "GOOD" to "رابح",
            "POOR" to "هامش ضعيف",
            "LOSS" to "خسارة",
            "UNKNOWN" to "مسافة؟"
        )

        // Same verdict colours as the floating badges.
        fun levelColor(level: String): Int = Color.parseColor(
            when (level) {
                "EXCELLENT" -> "#22C55E"
                "GOOD" -> "#4ADE80"
                "POOR" -> "#F59E0B"
                "LOSS" -> "#F87171"
                else -> "#94A3B8"
            }
        )
    }
}
