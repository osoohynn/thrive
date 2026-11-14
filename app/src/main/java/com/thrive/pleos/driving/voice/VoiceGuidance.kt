package com.thrive.pleos.driving.voice

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.util.Log
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal enum class VoicePhase { Idle, Connecting, Preparing, Speaking, Done, Stopped, Failed }
internal data class VoiceState(
    val phase: VoicePhase = VoicePhase.Idle,
    val message: String = "듣기 버튼을 누르면 짧은 안내를 재생해요.",
) {
    val busy: Boolean get() = phase in setOf(VoicePhase.Connecting, VoicePhase.Preparing, VoicePhase.Speaking)
}

/** SDK callbacks have no utterance ID; a fresh client/listener isolates each accepted request. */
internal class VoiceGuidance(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val gate = VoiceRequestGate()
    private val mutableState = MutableStateFlow(VoiceState())
    val state = mutableState.asStateFlow()
    private var client: TextToSpeech? = null
    private var listener: EventListener? = null
    private var watchdog: Job? = null
    private var mutedByUser = false

    fun play(key: String, text: String, enabled: Boolean) {
        val audible = enabled && !mutedByUser
        val token = gate.begin(key, SystemClock.elapsedRealtime(), audible)
        if (token == null) {
            if (!state.value.busy) update(if (audible) VoicePhase.Stopped else VoicePhase.Idle,
                if (audible) "같은 안내는 10초 뒤 다시 들을 수 있어요." else "음성 안내가 꺼져 있어요.")
            return
        }
        update(VoicePhase.Connecting, "음성 서비스를 연결하고 있어요.")
        try {
            val service = TextToSpeech(context, Mode.ON_DEVICE)
            client = service
            val events = object : EventListener {
                override fun onReady() = Unit
                override fun onUpdatedRms(rms: Double) = Unit
                override fun onStart() = dispatch(token) {
                    update(VoicePhase.Speaking, "안내를 재생하고 있어요.")
                    timeout(token, 20_000, "재생 완료 응답이 없어 안내를 중단했어요.")
                }
                override fun onDone() = dispatch(token) { complete(token, VoicePhase.Done, "안내 재생을 마쳤어요.") }
                override fun onStop() = dispatch(token) { complete(token, VoicePhase.Stopped, "안내 재생이 중단됐어요.") }
                override fun onError(errorType: ErrorType) = dispatch(token) { fail(token, "음성을 재생하지 못했어요 ($errorType).") }
            }
            listener = events
            timeout(token, VoiceRequestGate.MAX_PENDING_MS, "연결이 늦어 안내 요청을 취소했어요. 다시 시도해 주세요.")
            service.initialize(object : OnInitializeListener {
                override fun onSuccess() = dispatch(token) {
                    if (state.value.phase != VoicePhase.Connecting) return@dispatch
                    if (!gate.isFresh(token, SystemClock.elapsedRealtime())) {
                        fail(token, "오래된 안내 요청을 취소했어요. 다시 선택해 주세요.")
                        return@dispatch
                    }
                    try {
                        service.addEventListener(events)
                        service.setLocale(Locale.KOREA)
                        service.setAudioAttributes(AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        update(VoicePhase.Preparing, "기기에서 음성을 준비하고 있어요.")
                        timeout(token, 8_000, "재생 시작 응답이 없어 안내를 중단했어요.")
                        service.speak(text, true)
                    } catch (error: Exception) { fail(token, "음성을 요청하지 못했어요 (${error.javaClass.simpleName}).") }
                }
                override fun onFailure(errorType: ErrorType) = dispatch(token) { fail(token, "음성 서비스를 사용할 수 없어요 ($errorType).") }
            })
        } catch (error: Exception) { fail(token, "음성을 연결하지 못했어요 (${error.javaClass.simpleName}).") }
    }

    private fun dispatch(token: Long, action: () -> Unit) {
        scope.launch { if (gate.isCurrent(token)) action() }
    }

    private fun timeout(token: Long, millis: Long, message: String) {
        watchdog?.cancel()
        watchdog = scope.launch {
            delay(millis)
            if (gate.isCurrent(token)) fail(token, message)
        }
    }

    private fun fail(token: Long, message: String) = complete(token, VoicePhase.Failed, message)

    private fun complete(token: Long, phase: VoicePhase, message: String) {
        if (!gate.isCurrent(token)) return
        gate.finish(token)
        val released = releaseClient()
        update(if (released) phase else VoicePhase.Failed,
            if (released) message else "$message 중단·해제 요청을 완료하지 못했어요.")
    }

    fun setMuted(muted: Boolean) {
        // Apply silence immediately, including while the preference write is still pending.
        mutedByUser = muted
        if (muted) stop()
    }

    fun stop() {
        val wasBusy = state.value.busy
        gate.cancel()
        val released = releaseClient()
        if (!released) update(VoicePhase.Failed, "음성 중단·해제 요청을 완료하지 못했어요.")
        else if (wasBusy) update(VoicePhase.Stopped, "안내 재생을 중단했어요.")
    }

    private fun releaseClient(): Boolean {
        watchdog?.cancel(); watchdog = null
        val service = client ?: return true
        val events = listener
        client = null; listener = null
        var released = true
        fun attempt(action: () -> Unit) {
            try { action() }
            catch (error: Exception) {
                released = false
                Log.w("ThriveVoice", "Release failed: ${error.javaClass.simpleName}")
            }
        }
        if (events != null) attempt { service.removeEventListener(events) }
        attempt { service.stop() }
        attempt { service.release() }
        return released
    }

    private fun update(phase: VoicePhase, message: String) {
        mutableState.value = VoiceState(phase, message)
        Log.d("ThriveVoice", "phase=$phase")
    }
}
