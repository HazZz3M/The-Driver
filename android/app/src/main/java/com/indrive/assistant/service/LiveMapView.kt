package com.indrive.assistant.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.indrive.assistant.InDriveAssistantApp
import com.indrive.assistant.data.model.HudPrefs
import com.indrive.assistant.engine.BidAdvisor
import com.indrive.assistant.engine.FareCalculatorEngine
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The live requests map (OpenStreetMap): every request as a pin at its pickup with its route to the
 * destination as inDrive draws it, coloured by verdict. Positions come from inDrive's own map and
 * the driver's GPS (RouteLocator), not from the addresses. Tapping a pin (or a request in the strip
 * at the bottom) shows its details and the actions: accept, hide, open, and inDrive's price buttons. Used by the floating window and by [LiveMapActivity].
 *
 * Main thread only. Call [start] when shown and [stop] when hidden.
 */
@SuppressLint("ViewConstructor")
class LiveMapView(context: Context, private val compact: Boolean) : FrameLayout(context) {

    private val map: MapView
    private val strip = LinearLayout(context)
    private val stripScroll = HorizontalScrollView(context)
    private val panel = LinearLayout(context)
    private val countChip = TextView(context)
    private val handler = Handler(Looper.getMainLooper())
    private var meMarker: Marker? = null

    private var selectedKey: String? = null
    private var confirmKey: String? = null
    private var confirmUntil = 0L
    /** The request whose own-price controls are open, and the price chosen there. */
    private var customKey: String? = null
    private var customValue = 0
    private var fittedOnce = false
    private var running = false
    private var lastSig = ""

    private val listener: () -> Unit = { refresh() }
    private val ticker = object : Runnable {
        override fun run() {
            refresh()
            updateMe()
            handler.postDelayed(this, 1000)
        }
    }

    init {
        val config = Configuration.getInstance()
        config.userAgentValue = context.packageName
        val base = File(context.cacheDir, "osmdroid")
        config.osmdroidBasePath = base
        config.osmdroidTileCache = File(base, "tiles")

        map = MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(12.0)
            val me = DriverLocation.last
            controller.setCenter(if (me != null) GeoPoint(me[0], me[1]) else GeoPoint(27.18, 31.18))
        }
        addView(map, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        // Top: number of requests and "show all".
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), 0)
        }
        countChip.apply {
            setTextColor(Color.WHITE)
            textSize = 12f
            background = pill(COLOR_INK, 0.88f)
            setPadding(dp(10), dp(5), dp(10), dp(5))
        }
        top.addView(countChip)
        top.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        top.addView(button("عرض الكل", COLOR_INK) { fitAll() })
        addView(top, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))

        // Bottom: the selected request, or a strip of all requests.
        val bottom = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        panel.apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(COLOR_INK, 0.95f, 14)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            visibility = View.GONE
        }
        val panelParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        panelParams.setMargins(dp(8), 0, dp(8), dp(6))
        bottom.addView(panel, panelParams)
        strip.orientation = LinearLayout.HORIZONTAL
        strip.setPadding(dp(6), 0, dp(6), dp(8))
        stripScroll.isHorizontalScrollBarEnabled = false
        stripScroll.addView(strip)
        bottom.addView(stripScroll)
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
    }

    fun start() {
        if (running) return
        running = true
        lastSig = ""
        map.onResume()
        DriverLocation.start(context)
        updateMe()
        LiveRequests.addListener(listener)
        handler.post(ticker)
    }

    fun stop() {
        if (!running) return
        running = false
        handler.removeCallbacks(ticker)
        LiveRequests.removeListener(listener)
        map.onPause()
    }

    fun destroy() {
        stop()
        map.onDetach()
    }

    private fun prefs(): HudPrefs = InDriveAssistantApp.instance.repository.getHudPrefs()

    /** The driver's own position (the blue dot), from DriverLocation. */
    private fun updateMe() {
        val me = DriverLocation.last
        if (me == null) {
            meMarker?.let { map.overlays.remove(it) }
            meMarker = null
            return
        }
        val marker = meMarker ?: Marker(map).apply {
            icon = meDrawable()
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            infoWindow = null
            setOnMarkerClickListener { _, _ -> true }
        }.also { meMarker = it }
        marker.position = GeoPoint(me[0], me[1])
        if (!map.overlays.contains(marker)) map.overlays.add(marker)
        map.invalidate()
    }

    // =========================================================================
    // DRAWING
    // =========================================================================

    private fun refresh() {
        if (!running) return
        val keepMs = prefs().liveMapKeepSeconds * 1000L
        val list = LiveRequests.snapshot(keepMs)
        if (selectedKey != null && list.none { it.key == selectedKey }) selectedKey = null
        if (confirmKey != null && System.currentTimeMillis() >= confirmUntil) confirmKey = null

        // Redraw only when something visible changed (the ticker runs every second, and rebuilding
        // the buttons under the driver's finger would swallow the tap).
        val sig = list.joinToString(";") {
            "${it.key}|${it.status}|${it.onScreen}|${System.identityHashCode(LiveRequests.geoOf(it.key))}|${it.card.fare}|${it.level}|" +
                "${it.card.bidChips.map { b -> b.value }}|${it.analysis.netProfit.roundToInt()}|" +
                "${System.identityHashCode(LiveRequests.snapshotOf(it.key))}"
        } + "#$selectedKey#$confirmKey#$customKey:$customValue#${LiveRequests.openKey}#${prefs().boardShowApproach}"
        if (sig == lastSig) return
        lastSig = sig

        // Request overlays are rebuilt each time (a handful of requests); my position stays.
        map.overlays.removeAll { (it is Marker && it !== meMarker) || it is Polyline || it is RouteDots }
        val selected = list.firstOrNull { it.key == selectedKey }
        for (e in list.sortedBy { it.key == selectedKey }) { // the selected one is drawn last (on top)
            val geo = LiveRequests.geoOf(e.key) ?: continue
            val p = geo.pickup
            val faded = !e.onScreen || e.status != LiveRequests.STATUS_LIVE
            val color = levelColor(e.level)
            val from = GeoPoint(p[0], p[1])
            // The routes as inDrive draws them (the way to the pickup only if the driver wants it).
            if (prefs().boardShowApproach && geo.approach.isNotEmpty()) {
                map.overlays.add(RouteDots(geo.approach, Color.parseColor("#3B82F6"), if (faded) 90 else 200, dp(2).toFloat()))
            }
            if (geo.trip.isNotEmpty()) {
                map.overlays.add(RouteDots(geo.trip, color, if (faded) 110 else 235, dp(if (e.key == selectedKey) 3 else 2).toFloat()))
            }
            geo.dropoff.let { d ->
                val to = GeoPoint(d[0], d[1])
                // A straight line only when the route itself couldn't be read.
                if (geo.trip.isEmpty()) map.overlays.add(Polyline(map).apply {
                    setPoints(listOf(from, to))
                    outlinePaint.color = color
                    outlinePaint.alpha = if (faded) 90 else 220
                    outlinePaint.strokeWidth = dp(if (e.key == selectedKey) 5 else 3).toFloat()
                    outlinePaint.strokeCap = Paint.Cap.ROUND
                    if (faded) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(dp(6).toFloat(), dp(6).toFloat()), 0f)
                    setOnClickListener { _, _, _ -> select(e.key); true }
                })
                map.overlays.add(Marker(map).apply {
                    position = to
                    icon = dotDrawable(color, faded)
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    infoWindow = null
                    setOnMarkerClickListener { _, _ -> select(e.key); true }
                })
            }
            map.overlays.add(Marker(map).apply {
                position = from
                icon = pinDrawable("${e.card.fare.roundToInt()}", color, faded, e.key == selectedKey)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                infoWindow = null
                setOnMarkerClickListener { _, _ -> select(e.key); true }
            })
        }
        map.invalidate()

        val live = list.count { it.onScreen }
        countChip.text = if (list.isEmpty()) "مفيش طلبات لسه • افتح إن درايف" else "${list.size} طلب • $live ظاهر في إن درايف"
        if (!fittedOnce && list.any { LiveRequests.geoOf(it.key) != null }) {
            fittedOnce = true
            map.post { fitAll() }
        }
        renderStrip(list)
        renderPanel(selected)
    }

    private fun renderStrip(list: List<LiveRequests.Entry>) {
        strip.removeAllViews()
        stripScroll.visibility = if (selectedKey == null && list.isNotEmpty()) View.VISIBLE else View.GONE
        for (e in list) {
            val chip = TextView(context).apply {
                val where = if (LiveRequests.geoOf(e.key) == null) "📍؟ " else ""
                text = "$where${e.card.fare.roundToInt()} • ${LEVEL_LABELS[e.level]}\n${shortPlace(e.card.dropoffAddress)}"
                setTextColor(Color.WHITE)
                textSize = 12f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                background = rounded(levelColor(e.level), if (e.onScreen) 0.92f else 0.45f, 12)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setOnClickListener { select(e.key, center = true) }
            }
            val lp = LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(6)
            strip.addView(chip, lp)
        }
    }

    private fun renderPanel(e: LiveRequests.Entry?) {
        panel.removeAllViews()
        if (e == null) {
            panel.visibility = View.GONE
            return
        }
        panel.visibility = View.VISIBLE
        val a = e.analysis
        val c = e.card
        val approx = if (c.distanceEstimated) "≈ " else ""

        // Title row: fare, verdict, close.
        val title = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        title.addView(text("${c.fare.roundToInt()} ج.م", 18f, Color.WHITE, bold = true))
        title.addView(text("  ${LEVEL_LABELS[e.level]} • صافي $approx${a.netProfit.roundToInt()}", 13f, levelColor(e.level), bold = true))
        title.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        title.addView(text("✕", 16f, Color.LTGRAY).apply {
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { select(null) }
        })
        panel.addView(title)

        val who = listOf(c.passengerName, c.passengerRating.takeIf { it.isNotBlank() }?.let { "★ $it" }).filterNotNull()
            .filter { it.isNotBlank() }.joinToString("  ")
        if (who.isNotBlank()) panel.addView(text(who, 12.5f, Color.LTGRAY))
        panel.addView(text("من: ${c.pickupAddress.ifBlank { "—" }}", 12.5f, Color.WHITE, lines = if (compact) 1 else 2))
        panel.addView(text("إلى: ${c.dropoffAddress.ifBlank { "—" }}", 12.5f, Color.WHITE, lines = if (compact) 1 else 2))
        val km = "الوصول ${if (c.pickupKnown) fmt(c.pickupDistanceKm) else "؟"} كم • المشوار ${if (c.tripKnown) fmt(c.tripDistanceKm) else "؟"} كم"
        val state = when {
            e.status != LiveRequests.STATUS_LIVE -> e.statusDetail
            !e.onScreen -> "مش ظاهر في إن درايف دلوقتي"
            LiveRequests.openKey == e.key -> "مفتوح في إن درايف"
            else -> "ظاهر في إن درايف"
        }
        panel.addView(text("$km • $state", 11.5f, Color.parseColor("#94A3B8")))
        if (LiveRequests.geoOf(e.key) == null) {
            val why = if (DriverLocation.last == null) "فعّل إذن الموقع عشان الطلبات تتحط على الخريطة"
            else "مكانه بيتحدد أول ما الطلب يتفتح في إن درايف"
            panel.addView(text(why, 11.5f, Color.parseColor("#FBBF24")))
        }
        // inDrive's own map of this trip, to compare with.
        LiveRequests.snapshotOf(e.key)?.let { bmp ->
            panel.addView(ImageView(context).apply {
                setImageBitmap(bmp)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.BLACK, 1f, 8)
                clipToOutline = true
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(if (compact) 90 else 140)).apply { topMargin = dp(6) })
        }

        if (e.status == LiveRequests.STATUS_LIVE) {
            val actions = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
            val confirming = confirmKey == e.key && System.currentTimeMillis() < confirmUntil
            actions.addView(button(if (confirming) "اضغط تاني للتأكيد" else "قبول ${c.fare.roundToInt()}", COLOR_ACCEPT) {
                accept(e.key)
            }, weighted())
            actions.addView(button("إخفاء", COLOR_HIDE) { act(e.key, InDriveAccessibilityService.MAP_HIDE) }, weighted())
            if (LiveRequests.openKey != e.key) {
                actions.addView(button("اعرض سعر", COLOR_OPEN) { act(e.key, InDriveAccessibilityService.MAP_OPEN) }, weighted())
            }
            panel.addView(actions)

            // Counter-bid buttons, once the request was opened in inDrive (the best one is marked).
            if (c.bidChips.isNotEmpty()) {
                val settings = InDriveAssistantApp.instance.repository.getDriverSettings()
                val rated = BidAdvisor.rate(c, settings)
                val best = BidAdvisor.best(rated)?.chip?.value
                val bids = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
                for (r in rated) {
                    val v = r.chip.value
                    val label = (if (v == best) "★ " else "") + "$v\nصافي ${r.analysis.netProfit.roundToInt()}"
                    bids.addView(button(label, if (v == best) COLOR_EXCELLENT_INT else COLOR_BID) {
                        act(e.key, InDriveAccessibilityService.MAP_BID, v)
                    }, weighted())
                }
                panel.addView(bids)
            }

            // The driver's own price (typed into inDrive's ✎ price sheet).
            val customOpen = customKey == e.key
            if (!customOpen) {
                panel.addView(LinearLayout(context).apply {
                    setPadding(0, dp(6), 0, 0)
                    addView(button("سعر تاني (اكتب سعرك)", COLOR_OPEN) {
                        customKey = e.key
                        customValue = startPrice(e)
                        lastSig = ""
                        refresh()
                    }, weighted())
                })
            } else {
                val net = FareCalculatorEngine.analyze(
                    pickupDistanceKm = c.pickupDistanceKm,
                    tripDistanceKm = c.tripDistanceKm,
                    estimatedMinutes = FareCalculatorEngine.estimateMinutes(c.totalDistanceKm),
                    offeredFare = customValue.toDouble(),
                    settings = InDriveAssistantApp.instance.repository.getDriverSettings(),
                    pickupAddress = c.pickupAddress,
                    dropoffAddress = c.dropoffAddress,
                    passengerName = c.passengerName
                ).netProfit.roundToInt()
                fun step(d: Int) = button(if (d > 0) "+$d" else "$d", COLOR_BID) {
                    customValue = (customValue + d).coerceIn(5, 5000)
                    lastSig = ""
                    refresh()
                }
                panel.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, dp(6), 0, 0)
                    addView(step(-10), weighted())
                    addView(step(-5), weighted())
                    addView(text("$customValue\nصافي $net", 14f, Color.WHITE, bold = true).apply { gravity = Gravity.CENTER }, weighted())
                    addView(step(5), weighted())
                    addView(step(10), weighted())
                })
                panel.addView(LinearLayout(context).apply {
                    setPadding(0, dp(6), 0, 0)
                    addView(button("ابعت $customValue ج.م", COLOR_ACCEPT) {
                        customKey = null
                        act(e.key, InDriveAccessibilityService.MAP_CUSTOM, customValue)
                    }, weighted())
                    addView(button("إلغاء", COLOR_BID) {
                        customKey = null
                        lastSig = ""
                        refresh()
                    }, weighted())
                })
            }
        }
    }

    // =========================================================================
    // ACTIONS
    // =========================================================================

    private fun select(key: String?, center: Boolean = false) {
        selectedKey = key
        confirmKey = null
        if (center && key != null) LiveRequests.geoOf(key)?.pickup?.let { map.controller.animateTo(GeoPoint(it[0], it[1])) }
        refresh()
    }

    /** Where the driver's own price starts: the passenger's fare, or the target price if higher, rounded up to 5. */
    private fun startPrice(e: LiveRequests.Entry): Int {
        val target = maxOf(e.card.fare, e.analysis.recommendedFare)
        return (kotlin.math.ceil(target / 5.0) * 5).toInt().coerceIn(5, 5000)
    }

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
            android.widget.Toast.makeText(context, "شغّل قراءة الشاشة الأول", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun fitAll() {
        val points = LiveRequests.snapshot(prefs().liveMapKeepSeconds * 1000L)
            .mapNotNull { LiveRequests.geoOf(it.key) }
            .flatMap { listOf(it.pickup, it.dropoff) }
            .map { GeoPoint(it[0], it[1]) }
            .toMutableList()
        DriverLocation.last?.let { points.add(GeoPoint(it[0], it[1])) }
        when {
            points.isEmpty() -> return
            points.size == 1 -> map.controller.animateTo(points[0], 14.0, 600L)
            else -> {
                val box = BoundingBox.fromGeoPoints(points)
                if (abs(box.latNorth - box.latSouth) < 0.002 && abs(box.lonEast - box.lonWest) < 0.002) {
                    map.controller.animateTo(points[0], 15.0, 600L)
                } else {
                    map.zoomToBoundingBox(box.increaseByScale(1.35f), true, dp(24))
                }
            }
        }
    }

    // =========================================================================
    // SMALL VIEW HELPERS
    // =========================================================================

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).roundToInt()

    private fun fmt(v: Double) = if (v >= 10) "${v.roundToInt()}" else String.format(java.util.Locale.US, "%.1f", v)

    /** "Place (street، neighbourhood، …)" → the neighbourhood-ish part, for the small strip. */
    private fun shortPlace(address: String): String {
        val open = address.indexOf('(')
        val inner = if (open >= 0) address.substring(open + 1).split('،', ',').map { it.trim() }.firstOrNull { it.length > 2 } else null
        return "← " + (inner ?: address).trim().take(28)
    }

    private fun weighted() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginEnd = dp(4)
    }

    private fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 2) = TextView(context).apply {
        text = s
        textSize = size
        setTextColor(color)
        maxLines = lines
        ellipsize = TextUtils.TruncateAt.END
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = TextView(context).apply {
        text = label
        textSize = 12.5f
        gravity = Gravity.CENTER
        setTextColor(Color.WHITE)
        typeface = Typeface.DEFAULT_BOLD
        background = rounded(color, 0.95f, 10)
        setPadding(dp(10), dp(7), dp(10), dp(7))
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, alpha: Float, radiusDp: Int) = GradientDrawable().apply {
        setColor(Color.argb((alpha * 255).roundToInt(), Color.red(color), Color.green(color), Color.blue(color)))
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun pill(color: Int, alpha: Float) = rounded(color, alpha, 20)

    /** A rounded label with the fare and a small pointer at the bottom (the pickup). */
    private fun pinDrawable(label: String, color: Int, faded: Boolean, selected: Boolean): BitmapDrawable {
        val density = resources.displayMetrics.density
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = (if (selected) 15f else 13f) * density
            typeface = Typeface.DEFAULT_BOLD
            this.color = Color.WHITE
        }
        val padH = 8 * density
        val padV = 4 * density
        val w = paint.measureText(label) + padH * 2
        val h = paint.textSize + padV * 2
        val tip = 6 * density
        val stroke = (if (selected) 3f else 1.5f) * density
        val bmp = Bitmap.createBitmap((w + stroke * 2).roundToInt(), (h + tip + stroke * 2).roundToInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = if (faded) 120 else 255 }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (selected) Color.WHITE else Color.parseColor(COLOR_INK_HEX)
            style = Paint.Style.STROKE
            strokeWidth = stroke
        }
        val rect = RectF(stroke, stroke, stroke + w, stroke + h)
        canvas.drawRoundRect(rect, h / 2, h / 2, fill)
        canvas.drawRoundRect(rect, h / 2, h / 2, border)
        val path = android.graphics.Path().apply {
            moveTo(rect.centerX() - tip, rect.bottom - 1)
            lineTo(rect.centerX(), rect.bottom + tip)
            lineTo(rect.centerX() + tip, rect.bottom - 1)
            close()
        }
        canvas.drawPath(path, fill)
        paint.alpha = if (faded) 200 else 255
        canvas.drawText(label, rect.left + padH, rect.top + padV - paint.ascent() * 0.92f, paint)
        return BitmapDrawable(resources, bmp)
    }

    /** The driver: a blue dot with a white ring. */
    private fun meDrawable(): BitmapDrawable {
        val size = (18 * resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = size / 2f
        canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
        canvas.drawCircle(r, r, r * 0.66f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#2563EB") })
        return BitmapDrawable(resources, bmp)
    }

    /** A route as many small dots (the points come unordered from inDrive's drawing). */
    private class RouteDots(points: List<DoubleArray>, color: Int, alpha: Int, private val radius: Float) : Overlay() {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).also {
            it.color = color
            it.alpha = alpha
        }
        private val geo = points.map { GeoPoint(it[0], it[1]) }
        private val pt = android.graphics.Point()

        override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
            if (shadow) return
            val projection = mapView.projection
            for (g in geo) {
                projection.toPixels(g, pt)
                canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), radius, paint)
            }
        }
    }

    /** The destination: a small ringed dot. */
    private fun dotDrawable(color: Int, faded: Boolean): BitmapDrawable {
        val size = (14 * resources.displayMetrics.density).roundToInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val r = size / 2f
        canvas.drawCircle(r, r, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE; alpha = if (faded) 140 else 255 })
        canvas.drawCircle(r, r, r * 0.62f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = if (faded) 120 else 255 })
        return BitmapDrawable(resources, bmp)
    }

    companion object {
        private const val COLOR_INK_HEX = "#0B0F17"
        private val COLOR_INK = Color.parseColor(COLOR_INK_HEX)
        private val COLOR_ACCEPT = Color.parseColor("#16A34A")
        private val COLOR_HIDE = Color.parseColor("#B91C1C")
        private val COLOR_OPEN = Color.parseColor("#4F46E5")
        private val COLOR_BID = Color.parseColor("#334155")
        private val COLOR_EXCELLENT_INT = Color.parseColor("#15803D")

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
