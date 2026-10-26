package com.indrive.assistant.data.local

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the automation did inside inDrive (accepted, hid, opened, tapped a bid), with the reason,
 * so the driver can see why a request was accepted or disappeared. Also holds the master
 * "automation paused" switch, which only the driver changes (app or notification).
 * Kept natively: the notification can pause automation without the Flutter app running.
 */
object AutomationLog {
    const val ACCEPT = "ACCEPT"
    const val HIDE = "HIDE"
    const val OPEN = "OPEN"
    const val BID = "BID"

    private const val PREFS = "automation"
    private const val KEY_LOG = "log"
    private const val KEY_PAUSED = "paused"
    private const val MAX = 300
    private val lock = Any()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isPaused(context: Context): Boolean = prefs(context).getBoolean(KEY_PAUSED, false)

    fun setPaused(context: Context, paused: Boolean) {
        prefs(context).edit().putBoolean(KEY_PAUSED, paused).apply()
    }

    fun add(context: Context, action: String, fare: Double, detail: String) {
        synchronized(lock) {
            val p = prefs(context)
            val arr = try { JSONArray(p.getString(KEY_LOG, "[]")) } catch (_: Exception) { JSONArray() }
            arr.put(JSONObject().put("t", System.currentTimeMillis()).put("action", action).put("fare", fare).put("detail", detail))
            val trimmed = if (arr.length() <= MAX) arr else JSONArray().also { out ->
                for (i in arr.length() - MAX until arr.length()) out.put(arr.get(i))
            }
            p.edit().putString(KEY_LOG, trimmed.toString()).apply()
        }
    }

    /** Oldest first, as a JSON array string. */
    fun asJson(context: Context): String = synchronized(lock) { prefs(context).getString(KEY_LOG, "[]") ?: "[]" }

    fun clear(context: Context) {
        synchronized(lock) { prefs(context).edit().remove(KEY_LOG).apply() }
    }
}
