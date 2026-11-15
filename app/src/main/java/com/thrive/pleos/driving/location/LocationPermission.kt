package com.thrive.pleos.driving.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

internal const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

internal fun Context.hasFusedLocationPermission(): Boolean =
    checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED

// The SDK permission alone does not satisfy Android's location foreground-service requirements.
// Require precise consent because this SDK does not provide an approximate-only stream.
internal fun Context.hasRecordingLocationPermission(): Boolean = hasFusedLocationPermission() &&
    checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
