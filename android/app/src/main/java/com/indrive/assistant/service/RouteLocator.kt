package com.indrive.assistant.service

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Places a request on the real map from inDrive's own Ride-request map, without looking up the
 * addresses: inDrive draws the driver's car, A (pickup), B (destination), the way to the pickup
 * in blue and the trip in lime, north up. With the driver's GPS position at the car and the map's
 * scale, every pixel of that map has a latitude / longitude.
 *
 * The scale comes from the routes themselves: inDrive tells their lengths (the pickup distance on
 * the card, the trip length in its info box), and the drawn length of each route in pixels is
 * measured on the screenshot (pixel count ÷ line thickness).
 */
object RouteLocator {

    class Geo(
        val pickup: DoubleArray,
        val dropoff: DoubleArray,
        /** Points along the trip A → B and along the way to the pickup, as [lat, lng]. */
        val trip: List<DoubleArray>,
        val approach: List<DoubleArray>,
        val metersPerPixel: Double
    )

    private const val METERS_PER_DEG_LAT = 111_320.0
    /** Roads are longer than the straight line: fallback when a route can't be measured. */
    private const val ROAD_FACTOR = 1.3
    private const val MAX_POINTS = 500

    /**
     * [shot] is the full screenshot (screen pixels). [map] is the visible part of inDrive's map;
     * [exclude] are boxes drawn over it (info boxes, markers) whose colours aren't route.
     * [me] is the driver's GPS position [lat, lng]. Returns null when it can't be placed.
     */
    fun locate(
        shot: Bitmap,
        map: Rect,
        car: Rect,
        a: Rect,
        b: Rect,
        exclude: List<Rect>,
        me: DoubleArray,
        pickupKm: Double?,
        tripKm: Double?
    ): Geo? {
        val r = Rect(map)
        if (!r.intersect(0, 0, shot.width, shot.height) || r.width() < 50 || r.height() < 50) return null
        val w = r.width()
        val h = r.height()
        val px = IntArray(w * h)
        shot.getPixels(px, 0, w, r.left, r.top, w, h)

        // 1 = trip (lime), 2 = way to the pickup (blue), 0 = anything else.
        val mask = ByteArray(w * h)
        val skip = exclude.map { Rect(it).apply { inset(-6, -6); offset(-r.left, -r.top) } }
        for (y in 0 until h) {
            for (x in 0 until w) {
                val c = px[y * w + x]
                val kind = when {
                    isTrip(c) -> 1
                    isApproach(c) -> 2
                    else -> 0
                }
                if (kind != 0 && skip.none { it.contains(x, y) }) mask[y * w + x] = kind.toByte()
            }
        }
        // Specks of the same colours elsewhere on the map (labels, parks) are not route.
        keepMainParts(mask, w, h, 1)
        keepMainParts(mask, w, h, 2)
        val tripPx = routeLengthPx(mask, w, h, 1)
        val approachPx = routeLengthPx(mask, w, h, 2)

        // Metres per pixel from the trip (A and B are kept on screen, so it is drawn whole); the
        // way to the pickup often runs off the map's edge, so it is only a fallback, and the
        // straight line the last resort.
        val estimates = mutableListOf<Double>()
        if (tripKm != null && tripKm > 0.1 && tripPx > 60) estimates.add(tripKm * 1000 / tripPx)
        else if (pickupKm != null && pickupKm > 0.1 && approachPx > 60) estimates.add(pickupKm * 1000 / approachPx)
        if (estimates.isEmpty()) {
            val straightA = hypot((a.centerX() - car.centerX()).toDouble(), (a.centerY() - car.centerY()).toDouble())
            val straightAB = hypot((b.centerX() - a.centerX()).toDouble(), (b.centerY() - a.centerY()).toDouble())
            if (pickupKm != null && straightA > 30) estimates.add(pickupKm * 1000 / ROAD_FACTOR / straightA)
            else if (tripKm != null && straightAB > 30) estimates.add(tripKm * 1000 / ROAD_FACTOR / straightAB)
        }
        if (estimates.isEmpty()) return null
        val mpp = estimates.average()

        fun toGeo(x: Int, y: Int): DoubleArray {
            val dy = (y - car.centerY()) * mpp
            val dx = (x - car.centerX()) * mpp
            val lat = me[0] - dy / METERS_PER_DEG_LAT
            val lng = me[1] + dx / (METERS_PER_DEG_LAT * cos(me[0] * PI / 180))
            return doubleArrayOf(lat, lng)
        }

        fun points(kind: Byte): List<DoubleArray> {
            val count = mask.count { it == kind }
            if (count == 0) return emptyList()
            // Evenly thinned so a long route stays light to draw.
            val step = max(1, sqrt(count.toDouble() / MAX_POINTS).toInt())
            val out = mutableListOf<DoubleArray>()
            var y = 0
            while (y < h) {
                var x = 0
                while (x < w) {
                    if (mask[y * w + x] == kind) out.add(toGeo(x + r.left, y + r.top))
                    x += step
                }
                y += step
            }
            return out
        }

        return Geo(
            pickup = toGeo(a.centerX(), a.bottom - a.height() / 4),
            dropoff = toGeo(b.centerX(), b.bottom - b.height() / 4),
            trip = points(1),
            approach = points(2),
            metersPerPixel = mpp
        )
    }

    /**
     * Keeps only the big connected pieces of one route colour (a route may be cut in a few pieces
     * by markers and boxes; anything under a fifth of the biggest piece is noise).
     */
    private fun keepMainParts(mask: ByteArray, w: Int, h: Int, kind: Int) {
        val k = kind.toByte()
        val label = IntArray(w * h)
        val sizes = mutableListOf(0) // sizes[id], id 0 = none
        val queue = IntArray(w * h)
        for (start in 0 until w * h) {
            if (mask[start] != k || label[start] != 0) continue
            val id = sizes.size
            var head = 0
            var tail = 0
            queue[tail++] = start
            label[start] = id
            while (head < tail) {
                val i = queue[head++]
                val x = i % w
                val y = i / w
                // 8-neighbours: thin diagonal parts stay connected.
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (mask[j] == k && label[j] == 0) {
                        label[j] = id
                        queue[tail++] = j
                    }
                }
            }
            sizes.add(tail)
        }
        val biggest = sizes.maxOrNull() ?: return
        if (biggest == 0) return
        for (i in 0 until w * h) {
            val id = label[i]
            if (id != 0 && sizes[id] * 5 < biggest) mask[i] = 0
        }
    }

    /** inDrive's lime route (#C1F11D-ish): bright green-yellow. */
    private fun isTrip(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return g > 190 && r in 140..235 && b < 110 && g - b > 120
    }

    /** inDrive's blue route to the pickup (#3E7BF6-ish), not the darker blue of rivers. */
    private fun isApproach(c: Int): Boolean {
        val r = Color.red(c)
        val g = Color.green(c)
        val b = Color.blue(c)
        return b > 200 && r < 120 && g in 90..190 && b - r > 110
    }

    /**
     * Drawn length of a route in pixels: its area divided by its thickness. The thickness of a
     * line crossing a pixel at any angle is h·v / √(h² + v²), with h and v the horizontal and
     * vertical runs through it; the median over the route is used.
     */
    private fun routeLengthPx(mask: ByteArray, w: Int, h: Int, kind: Int): Double {
        val k = kind.toByte()
        var area = 0
        val thickness = mutableListOf<Double>()
        var seen = 0
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y * w + x] != k) continue
                area++
                seen++
                if (seen % 7 != 0) continue // a sample is enough for the thickness
                var l = x
                while (l > 0 && mask[y * w + l - 1] == k) l--
                var rr = x
                while (rr < w - 1 && mask[y * w + rr + 1] == k) rr++
                var t = y
                while (t > 0 && mask[(t - 1) * w + x] == k) t--
                var bt = y
                while (bt < h - 1 && mask[(bt + 1) * w + x] == k) bt++
                val hr = (rr - l + 1).toDouble()
                val vr = (bt - t + 1).toDouble()
                thickness.add(hr * vr / sqrt(hr * hr + vr * vr))
            }
        }
        if (area < 30 || thickness.isEmpty()) return 0.0
        thickness.sort()
        val t = thickness[thickness.size / 2].coerceAtLeast(1.0)
        return area / t
    }
}
