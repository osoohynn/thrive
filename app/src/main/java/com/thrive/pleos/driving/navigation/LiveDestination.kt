package com.thrive.pleos.driving.navigation

import com.thrive.pleos.driving.location.LocationTrack

internal data class LiveDestination(val name: String, val latitude: Double, val longitude: Double) {
    val valid: Boolean get() = name.isNotBlank() && LocationTrack.validCoordinates(latitude, longitude)
}
