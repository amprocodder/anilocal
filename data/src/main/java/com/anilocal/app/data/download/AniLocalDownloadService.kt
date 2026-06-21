package com.anilocal.app.data.download

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.anilocal.app.data.R
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Unique JobScheduler id for resuming downloads in the background / after reboot. */
private const val DOWNLOAD_JOB_ID = 1

/**
 * Foreground service that runs Media3 downloads + shows the progress notification.
 * Instantiated by the framework, so its dependencies are pulled via a Hilt EntryPoint.
 */
@OptIn(UnstableApi::class)
class AniLocalDownloadService : DownloadService(
    /* foregroundNotificationId = */ 1,
    DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    DOWNLOAD_CHANNEL_ID,
    R.string.download_channel_name,
    0,
) {
    private val entryPoint
        get() = EntryPointAccessors.fromApplication(applicationContext, DownloadEntryPoint::class.java)

    override fun getDownloadManager(): DownloadManager = entryPoint.downloadManager()

    // JobScheduler-backed: persists across reboot (requires RECEIVE_BOOT_COMPLETED) and
    // restarts the service to resume downloads when the manager's requirements are met.
    override fun getScheduler(): Scheduler = PlatformScheduler(this, DOWNLOAD_JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirementFlags: Int,
    ): Notification = entryPoint.downloadNotificationHelper().buildProgressNotification(
        this,
        android.R.drawable.stat_sys_download,
        null,
        null,
        downloads,
        notMetRequirementFlags,
    )
}

@OptIn(UnstableApi::class)
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadEntryPoint {
    fun downloadManager(): DownloadManager
    fun downloadNotificationHelper(): androidx.media3.exoplayer.offline.DownloadNotificationHelper
}
