package com.ijmacd.solarlogger

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import java.lang.ref.WeakReference
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.time.format.DateTimeFormatter.ISO_DATE_TIME
import android.content.ComponentName
import android.view.View
import android.R.attr.opacity
import android.graphics.Color


const val UPDATE_WIDGET_ACTION = "update_widget"

class SolarLoggerWidgetProvider : AppWidgetProvider() {

    class SolarDataPoint (val date : Date?, val voltage : Float)

    private class UpdaterTask (context: Context?, appWidgetManager: AppWidgetManager?, val appWidgetIds: IntArray?) : AsyncTask<String, Int, SolarDataPoint>() {
        private val contextRef = WeakReference<Context>(context)
        private val appWidgetManagerRef = WeakReference<AppWidgetManager>(appWidgetManager)

        override fun doInBackground(vararg urls: String?): SolarDataPoint {
            val url = URL(urls[0])

            val data = url.readText()

            val lines = data.lines()

            if (lines.size < 2) {
                throw Exception("Invalid Data")
            }

            val lastLine = lines[lines.size - 2] // Last line will actually be blank due to trailing '\n'

            val row = lastLine.split("\t")

            if (row.size < 2) {
                throw Exception("Invalid Data")
            }

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val accessor = ISO_DATE_TIME.parse(row[0])

                SolarDataPoint(Date.from(Instant.from(accessor)), java.lang.Float.parseFloat(row[1]))
            } else {
                SolarDataPoint(null, java.lang.Float.parseFloat(row[1]))
            }
        }

        override fun onPostExecute(dataPoint: SolarDataPoint?) {

            val context : Context = contextRef.get() ?: return

            appWidgetIds?.forEach { appWidgetId ->
                val views: RemoteViews = RemoteViews(
                    context.packageName,
                    R.layout.appwidget
                ).apply {
                    val intent = Intent(context, SolarLoggerWidgetProvider::class.java)
                    intent.action = UPDATE_WIDGET_ACTION
                    val pendingIntent  = PendingIntent.getBroadcast(context, 0, intent, 0)
                    setOnClickPendingIntent(R.id.appwidget_layout, pendingIntent)
                    setTextViewText(R.id.voltage, dataPoint?.voltage.toString () + "v")
                    setTextViewText(R.id.date, dataPoint?.date.toString().substring(0,19))
                    setInt(R.id.appwidget_layout, "setBackgroundColor", Color.WHITE)
                    if(dataPoint?.date != null && Date().time - dataPoint.date.time > 3600 * 1000) {
                        setTextColor(R.id.voltage, Color.RED)
                    }
                }

                appWidgetManagerRef.get()?.updateAppWidget(appWidgetId, views)
            }
        }

    }

    override fun onUpdate(context: Context?, appWidgetManager: AppWidgetManager?, appWidgetIds: IntArray?) {
        Log.d("SolarLogger", "Updating")
        val task = UpdaterTask(context, appWidgetManager, appWidgetIds)

        task.execute("https://ijmacd.com/solar.php?method=data&limit=1")

        // Show pending state
        appWidgetIds?.forEach { appWidgetId ->
            val views: RemoteViews = RemoteViews(
                context?.packageName,
                R.layout.appwidget
            ).apply {
                val opacity = 0.3f              // opacity = 0: fully transparent, opacity = 1: no transparancy
                val backgroundColor = 0xFFFFFF  // background color (here black)
                val withAlpha = (opacity * 0xFF).toInt() shl 24 or backgroundColor
                setInt(R.id.appwidget_layout, "setBackgroundColor", withAlpha)
                setTextViewText(R.id.voltage, " ")
                setTextViewText(R.id.date, "")
            }

            appWidgetManager?.updateAppWidget(appWidgetId, views)
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context !== null && intent?.action === UPDATE_WIDGET_ACTION) {
            val man = AppWidgetManager.getInstance(context)
            val ids = man.getAppWidgetIds(
                ComponentName(context, SolarLoggerWidgetProvider::class.java)
            )
            this.onUpdate(context, man, ids)
        } else {
            super.onReceive(context, intent)
        }
    }
}