// QluCampus 0.2.3, GPL-3.0.
package com.dawncourse.feature.settings

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent

internal const val WIDGET_PIN_PREFS = "widget_pin_confirmation"
internal const val WIDGET_PIN_PENDING = "pending_token"
internal const val WIDGET_PIN_CONFIRMED = "confirmed_token"
private const val PIN_CONFIRMED_ACTION = "com.qlucampus.app.WIDGET_PIN_CONFIRMED"
private const val PIN_TOKEN = "request_token"

internal class AndroidWidgetPinHost(private val context: Context) : WidgetPinHost {
    private val manager = AppWidgetManager.getInstance(context)

    override fun supportsPinning() = manager.isRequestPinAppWidgetSupported

    override fun requestPin(token: String): Boolean {
        context.getSharedPreferences(WIDGET_PIN_PREFS, Context.MODE_PRIVATE).edit()
            .putString(WIDGET_PIN_PENDING, token).apply()
        val callback = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, WidgetPinConfirmationReceiver::class.java)
                .setAction(PIN_CONFIRMED_ACTION).putExtra(PIN_TOKEN, token),
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        return try {
            manager.requestPinAppWidget(
                ComponentName(context.packageName, "com.dawncourse.feature.widget.DawnWidgetReceiver"), null, callback
            ).also { accepted -> if (!accepted) callback.cancel() }
        } catch (error: RuntimeException) {
            callback.cancel()
            throw error
        }
    }
}

/** Only the explicit immutable PendingIntent given to the launcher can invoke this receiver. */
class WidgetPinConfirmationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PIN_CONFIRMED_ACTION) return
        val token = intent.getStringExtra(PIN_TOKEN)?.takeIf { it.isNotBlank() } ?: return
        val preferences = context.getSharedPreferences(WIDGET_PIN_PREFS, Context.MODE_PRIVATE)
        if (preferences.getString(WIDGET_PIN_PENDING, null) != token) return
        preferences.edit().putString(WIDGET_PIN_CONFIRMED, token).apply()
    }
}
