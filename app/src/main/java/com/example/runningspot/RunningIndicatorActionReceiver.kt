package com.example.runningspot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RunningIndicatorActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        val activityIntent = Intent(context, RunningActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("indicator_action", action)
        }

        context.startActivity(activityIntent)
    }

    companion object {
        const val ACTION_PAUSE_RESUME = "com.example.runningspot.ACTION_PAUSE_RESUME"
        const val ACTION_STOP = "com.example.runningspot.ACTION_STOP"
    }
}