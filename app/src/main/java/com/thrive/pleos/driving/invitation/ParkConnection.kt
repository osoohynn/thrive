package com.thrive.pleos.driving.invitation

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.*
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Read-only GEAR_SELECTION. The Pleos Gear API is deprecated in favour of this property. */
// Keep listener/connection entry points compatible with API 28–34.
@Suppress("DEPRECATION")
internal class ParkConnection(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val mutableState = MutableStateFlow(ParkState())
    val state = mutableState.asStateFlow()
    private var car: Car? = null
    private var manager: CarPropertyManager? = null
    private var listener: CarPropertyManager.CarPropertyEventCallback? = null
    private var generation = 0
    private var request = 0
    private var timeout: Job? = null
    private var readJob: Job? = null

    fun connect() {
        release()
        if (context.checkSelfPermission(CAR_DRIVE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            mutableState.value = ParkState(message = "주차 상태 확인을 위해 차량 주행 정보 권한이 필요해요.")
            return
        }
        val token = generation
        mutableState.value = ParkState(querying = true, message = "차량 상태에 연결하고 있어요.")
        try {
            val connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName, service: IBinder) { scope.launch {
                    if (generation != token) return@launch
                    timeout?.cancel()
                    try {
                        val properties = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                            ?: error("차량 속성 서비스를 사용할 수 없어요.")
                        manager = properties
                        val events = object : CarPropertyManager.CarPropertyEventCallback {
                            override fun onChangeEvent(value: CarPropertyValue<*>?) { scope.launch {
                                if (generation != token || value?.propertyId != VehiclePropertyIds.GEAR_SELECTION || value.areaId != 0) return@launch
                                request++; timeout?.cancel(); readJob?.cancel()
                                received(value)
                            } }
                            override fun onErrorEvent(propertyId: Int, areaId: Int) { scope.launch {
                                if (generation != token || propertyId != VehiclePropertyIds.GEAR_SELECTION || areaId != 0) return@launch
                                request++; timeout?.cancel(); readJob?.cancel()
                                failed(IllegalStateException("기어 정보를 받을 수 없어요."))
                            } }
                        }
                        listener = events
                        check(properties.registerCallback(events, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)) {
                            "이 차량에서 기어 정보를 제공하지 않아요."
                        }
                        mutableState.value = ParkState()
                        query()
                    } catch (error: Exception) { failed(error) }
                } }
                override fun onServiceDisconnected(name: ComponentName) { scope.launch {
                    if (generation == token) {
                        request++; timeout?.cancel(); readJob?.cancel(); manager = null
                        mutableState.value = ParkState(message = "차량 연결이 끊겼어요. 제안을 보내지 않아요.")
                    }
                } }
            }
            // This connection overload also supports our API 28 minimum.
            car = Car.createCar(context, connection)
            timeout = scope.launch {
                delay(8_000)
                if (generation == token && manager == null) mutableState.value = ParkState(message = "차량 연결 응답이 없어요. 다시 확인해 주세요.")
            }
            car?.connect() ?: error("차량 서비스를 사용할 수 없어요.")
        } catch (error: Exception) { release(); failed(error) }
    }

    fun query() {
        if (state.value.querying) return
        val properties = manager ?: return
        val token = generation; val id = ++request
        mutableState.value = state.value.copy(querying = true)
        timeout?.cancel()
        timeout = scope.launch {
            delay(5_000)
            if (generation == token && request == id) {
                request++; readJob?.cancel()
                mutableState.value = ParkState(message = "주차 상태 응답을 받지 못했어요. 제안을 보내지 않아요.")
            }
        }
        readJob = scope.launch {
            try {
                val value = withContext(Dispatchers.IO) { properties.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, 0) }
                if (generation == token && request == id) { timeout?.cancel(); received(value) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                if (generation == token && request == id) { timeout?.cancel(); failed(error) }
            }
        }
    }

    private fun received(value: CarPropertyValue<*>?) {
        val gear = if (value?.status == CarPropertyValue.STATUS_AVAILABLE) value.value as? Int else null
        val name = gear?.let(VehicleGear::toString)
        Log.d("ThriveInvitation", "gear_property=${name ?: "NO_DATA"}")
        mutableState.value = ParkState(name, SystemClock.elapsedRealtime(), message = when (gear) {
            null -> "기어 상태가 미수신이에요. 제안을 보내지 않아요."
            VehicleGear.GEAR_PARK -> "주차(P) 상태를 확인했어요."
            else -> "주차(P) 상태가 아니어서 제안을 보내지 않아요."
        })
    }
    private fun failed(error: Exception) {
        mutableState.value = ParkState(message = "차량 상태를 확인하지 못했어요 (${error.javaClass.simpleName}). 제안을 보내지 않아요.")
    }
    fun release() {
        generation++; request++; timeout?.cancel(); readJob?.cancel()
        val properties = manager; val events = listener; val client = car
        manager = null; listener = null; car = null
        try { if (events != null) properties?.unregisterCallback(events) }
        catch (error: Exception) { Log.w("ThriveInvitation", "Gear release: ${error.javaClass.simpleName}") }
        try { client?.disconnect() }
        catch (error: Exception) { Log.w("ThriveInvitation", "Car release: ${error.javaClass.simpleName}") }
        mutableState.value = ParkState()
    }
}
