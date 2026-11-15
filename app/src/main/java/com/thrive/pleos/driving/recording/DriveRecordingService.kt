package com.thrive.pleos.driving.recording

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.thrive.pleos.MainActivity
import com.thrive.pleos.R

/** Started from a visible, user-approved recording action, never from boot or a map broadcast. */
internal class DriveRecordingService : Service() {
    private val recorder by lazy { DriveRecorder.get(this) }
    private var sessionId: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(SESSION_ID)
        if (id.isNullOrBlank()) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_FINISH) {
            recorder.finishFromNotification(id)
            if (sessionId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if (!recorder.expectsForeground(id)) {
            if (sessionId == null) stopSelfResult(startId)
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "주행 기록", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "사용자가 시작한 주행 기록의 진행 상태와 종료 버튼"; setShowBadge(false); setSound(null, null); enableVibration(false) })
            val open = PendingIntent.getActivity(this, 0,
                Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val finish = PendingIntent.getService(this, 0, Intent(this, DriveRecordingService::class.java)
                .setAction(ACTION_FINISH).setData(Uri.parse("thrive://recording/$id"))
                .putExtra(SESSION_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_drive_recording)
                .setContentTitle("주행을 기록하고 있어요")
                .setContentText("다른 앱에서도 기록을 이어가요. 눌러서 확인할 수 있어요.")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE).setVisibility(Notification.VISIBILITY_PRIVATE)
                .addAction(Notification.Action.Builder(null, "기록 종료", finish).build())
                .apply { if (Build.VERSION.SDK_INT >= 31) setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE) }
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            else startForeground(NOTIFICATION_ID, notification)
            sessionId = id
            recorder.foregroundStarted(id)
        } catch (error: Exception) {
            recorder.foregroundFailed(id, error)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sessionId?.let(recorder::foregroundStopped)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    companion object {
        const val SESSION_ID = "session_id"
        private const val CHANNEL_ID = "drive_recording_status"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_FINISH = "com.thrive.pleos.FINISH_RECORDING"
    }
}
