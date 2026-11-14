package com.thrive.pleos.driving.location

import android.content.Context
import android.content.pm.PackageManager

internal const val FUSED_LOCATION_PERMISSION = "pleos.car.permission.FUSED_LOCATION"

internal fun Context.hasFusedLocationPermission(): Boolean =
    checkSelfPermission(FUSED_LOCATION_PERMISSION) == PackageManager.PERMISSION_GRANTED
