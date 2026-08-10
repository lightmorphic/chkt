package org.chkt.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
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
        ids.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_record)
            val intent = PendingIntent.getActivity(
                context, 0,
                Intent(context, RecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_icon, intent)
            manager.updateAppWidget(id, views)
        }
    }
}
