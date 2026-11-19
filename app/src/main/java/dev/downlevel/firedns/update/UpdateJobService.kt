package dev.downlevel.firedns.update

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.util.Log
import dev.downlevel.firedns.FireDnsApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

/**
 * Background check, once a day with any network. It does not depend on the VPN being on,
 * and the request leaves outside the tunnel (FireDNS excludes itself from it).
 */
class UpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val container = (application as FireDnsApp).container
        container.appScope.launch {
            try {
                container.updateManager.check(notify = true)
            } catch (e: Exception) {
                Log.w("FireDNS", "Update check failed", e)
            } finally {
                jobFinished(params, false)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}

object UpdateScheduler {
    private const val JOB_ID = 1001

    /** Idempotent: an existing job is left alone, so the 24 h period does not restart every launch. */
    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java)
        if (scheduler.getPendingJob(JOB_ID) != null) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, UpdateJobService::class.java))
            .setPeriodic(TimeUnit.HOURS.toMillis(24))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setPersisted(true)
            .build()
        scheduler.schedule(job)
    }

    fun cancel(context: Context) {
        context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
}
