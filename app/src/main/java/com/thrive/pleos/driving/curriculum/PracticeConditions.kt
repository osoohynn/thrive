package com.thrive.pleos.driving.curriculum

import android.Manifest
import android.app.*
import android.app.job.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.thrive.pleos.MainActivity
import com.thrive.pleos.R
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

internal data class PracticeConditions(val checkedAt: Long, val isNight: Boolean, val rainMm: Double) {
    fun permits(kind: PracticeKind, now: Long): Boolean = now - checkedAt in 0..30 * 60_000 && when (kind) {
        PracticeKind.Night -> isNight
        PracticeKind.Rain -> rainMm > 0
        else -> true
    }
}
internal suspend fun fetchPracticeConditions(latitude: Double, longitude: Double): PracticeConditions = withContext(Dispatchers.IO) {
    require(latitude.isFinite() && latitude in -90.0..90.0 && longitude.isFinite() && longitude in -180.0..180.0)
    val connection = URL("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=is_day,rain,showers&timeformat=unixtime").openConnection() as HttpsURLConnection
    try {
        connection.connectTimeout = 10_000; connection.readTimeout = 10_000
        check(connection.responseCode == 200)
        val current = JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONObject("current")
        val day = current.getInt("is_day"); require(day in 0..1)
        val rain = current.getDouble("rain") + current.getDouble("showers"); require(rain.isFinite() && rain >= 0)
        PracticeConditions(current.getLong("time") * 1000, day == 0, rain)
    } finally { connection.disconnect() }
}
internal const val CURRICULUM_TOPIC_EXTRA = "curriculum_topic"
private const val JOB_ID = 3131
private const val CHANNEL = "practice_conditions"
internal class PracticeReminders(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("practice_conditions", Context.MODE_PRIVATE)
    val selected: Set<String> get() = prefs.getStringSet("items", emptySet()).orEmpty().toSet()
    fun enable(id: String, latitude: Double, longitude: Double) {
        require(practiceTopic(id)?.kind in setOf(PracticeKind.Night, PracticeKind.Rain))
        check(notificationsAllowed()) { "앱 설정에서 알림을 허용해 주세요." }
        check(prefs.edit().putString("latitude", latitude.toString()).putString("longitude", longitude.toString())
            .putLong("location_at", System.currentTimeMillis()).putStringSet("items", selected + id).commit())
        try { schedule() } catch (error: Exception) { disable(id); throw error }
    }
    fun disable(id: String) {
        check(prefs.edit().putStringSet("items", selected - id).commit())
        if (selected.isEmpty()) context.getSystemService(JobScheduler::class.java).cancel(JOB_ID)
    }
    fun notificationsAllowed() = (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()
    fun schedule() {
        if (selected.isEmpty()) return
        val job = JobInfo.Builder(JOB_ID, ComponentName(context, PracticeConditionJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30 * 60_000L).build()
        check(context.getSystemService(JobScheduler::class.java).schedule(job) == JobScheduler.RESULT_SUCCESS)
    }
    suspend fun checkAndNotify() {
        if (selected.isEmpty() || !notificationsAllowed()) return
        // A reminder checks the explicitly saved area; it does not track background location.
        if (System.currentTimeMillis() - prefs.getLong("location_at", 0) !in 0..7 * 24 * 60 * 60_000L) return
        val lat = prefs.getString("latitude", null)?.toDoubleOrNull() ?: return
        val lon = prefs.getString("longitude", null)?.toDoubleOrNull() ?: return
        val conditions = fetchPracticeConditions(lat, lon)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "조건별 연습 알림", NotificationManager.IMPORTANCE_DEFAULT))
        for (id in selected) {
            val topic = practiceTopic(id) ?: continue
            if (!conditions.permits(topic.kind, System.currentTimeMillis())) continue
            val intent = Intent(context, MainActivity::class.java).putExtra(CURRICULUM_TOPIC_EXTRA, id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pending = PendingIntent.getActivity(context, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(id.hashCode(), Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_drive_recording)
                .setContentTitle(if (topic.kind == PracticeKind.Rain) "빗길 연습 조건 알림" else "야간 연습 조건 알림")
                .setContentText("저장한 지역의 조건을 확인했어요. 정차 후 연습 항목을 확인해 주세요.")
                .setContentIntent(pending).setAutoCancel(true).build())
            disable(id) // One request produces one notification.
        }
    }
}
class PracticeConditionJob : JobService() {
    private var task: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        task = CoroutineScope(Dispatchers.IO).launch {
            var retry = false
            try { PracticeReminders(this@PracticeConditionJob).checkAndNotify() }
            catch (error: CancellationException) { throw error }
            catch (_: Exception) { retry = true }
            jobFinished(params, retry)
        }
        return true
    }
    override fun onStopJob(params: JobParameters): Boolean { task?.cancel(); return true }
}
