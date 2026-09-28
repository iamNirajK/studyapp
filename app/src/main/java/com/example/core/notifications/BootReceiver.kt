package com.example.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.core.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Log.d("BootReceiver", "Device reboot detected or package replaced. Restoring notification schedule...")
            
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val db = AppDatabase.getDatabase(context)
            val dao = db.scheduleDao()

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val schedule = dao.getScheduleByDateSync(todayDate)
                    val tasks = dao.getTasksByDateSync(todayDate)
                    
                    if (tasks.isNotEmpty()) {
                        Log.d("BootReceiver", "Found ${tasks.size} tasks for today. Scheduling alarms...")
                        StudyNotificationManager.scheduleAlarmsForTasks(
                            context = context,
                            tasks = tasks,
                            motivation = schedule?.motivation ?: "Let's achieve 100% completion today!"
                        )
                    } else {
                        Log.d("BootReceiver", "No tasks found for today ($todayDate) to reschedule.")
                    }
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error while restoring alarms: ${e.message}")
                }
            }
        }
    }
}
