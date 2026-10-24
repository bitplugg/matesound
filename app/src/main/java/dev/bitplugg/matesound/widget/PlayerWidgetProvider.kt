package dev.bitplugg.matesound.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import dev.bitplugg.matesound.R
import dev.bitplugg.matesound.playback.PlaybackService
import dev.bitplugg.matesound.ui.MainActivity

class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
        ids.forEach { updateWidget(context, appWidgetManager, it) }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "dev.bitplugg.matesound.ACTION_WIDGET_PLAY"
        const val ACTION_NEXT = "dev.bitplugg.matesound.ACTION_WIDGET_NEXT"
        const val ACTION_PREV = "dev.bitplugg.matesound.ACTION_WIDGET_PREV"

        fun updateAll(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
            ids.forEach { updateWidget(context, appWidgetManager, it) }
        }

        private fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val prefs = context.getSharedPreferences("matesound_widget", Context.MODE_PRIVATE)
            val title = prefs.getString("title", null).orEmpty().ifBlank { context.getString(R.string.app_name) }
            val artist = prefs.getString("artist", "").orEmpty()
            val playing = prefs.getBoolean("playing", false)

            val views = RemoteViews(context.packageName, R.layout.widget_player)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPending = PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            val playIntent = Intent(context, PlayerWidgetProvider::class.java).setAction(ACTION_PLAY_PAUSE)
            val playPending = PendingIntent.getBroadcast(context, 10, playIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_play, playPending)

            val nextIntent = Intent(context, PlayerWidgetProvider::class.java).setAction(ACTION_NEXT)
            val nextPending = PendingIntent.getBroadcast(context, 11, nextIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_next, nextPending)

            val prevIntent = Intent(context, PlayerWidgetProvider::class.java).setAction(ACTION_PREV)
            val prevPending = PendingIntent.getBroadcast(context, 12, prevIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_prev, prevPending)

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(R.id.widget_play, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
