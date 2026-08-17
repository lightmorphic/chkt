package org.chkt.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import org.chkt.app.R

/**
 * A one-cell widget that looks like a plain launcher icon. Tapping it opens
 * the tiny voice-capture overlay, nothing else lives here.
 */
class RecordWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, active = false)) }
    }

    companion object {
        private fun buildViews(context: Context, active: Boolean): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            views.setImageViewResource(
                R.id.widget_icon,
                if (active) R.drawable.ic_widget_mic_active else R.drawable.ic_widget_mic,
            )
            val intent = PendingIntent.getActivity(
                context, 0,
                Intent(context, RecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_icon, intent)
            return views
        }

        /** Swaps every placed instance of the widget to the green "listening"
         * icon, or back to the plain mic once capture ends. */
        fun setActive(context: Context, active: Boolean) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RecordWidgetReceiver::class.java))
            val views = buildViews(context, active)
            ids.forEach { id -> manager.updateAppWidget(id, views) }
        }
    }
}
