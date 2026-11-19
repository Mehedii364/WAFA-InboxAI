package com.example.data.jobs

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.example.WafaInboxApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Native Android JobService running periodic background reminder checks and connected-account
 * synchronization without requiring Firebase or third-party scheduling libraries.
 */
class WafaSyncJobService : JobService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        val app = application as? WafaInboxApplication ?: return false
        activeJob = serviceScope.launch {
            runCatching {
                app.repository.runScheduledBackgroundCycle()
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        activeJob?.cancel()
        return true
    }

    companion object {
        private const val SYNC_JOB_ID = 7701

        fun schedulePeriodicSync(context: Context, intervalMinutes: Int) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val periodMs = (intervalMinutes.coerceAtLeast(15) * 60L * 1000L)
            val component = ComponentName(context, WafaSyncJobService::class.java)
            val jobInfo = JobInfo.Builder(SYNC_JOB_ID, component)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(periodMs)
                .build()
            scheduler.schedule(jobInfo)
        }

        fun cancelPeriodicSync(context: Context) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            scheduler.cancel(SYNC_JOB_ID)
        }
    }
}
