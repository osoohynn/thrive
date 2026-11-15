package com.thrive.pleos.driving

import android.app.Instrumentation
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.thrive.pleos.MainActivity
import com.thrive.pleos.ui.driving.DrivingApp
import kotlinx.coroutines.delay

/** Production Compose UI under local constraints; does not change emulator/system settings. */
internal suspend fun checkUiRender(test: Instrumentation, width: Int, fontScale: Float): String {
    check(test.targetContext.packageName.endsWith(".verification"))
    check(width in 400..1600 && fontScale in 1f..1.5f)
    val activity = test.startActivitySync(Intent(test.targetContext, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
    test.runOnMainSync {
        activity.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                Box(Modifier.width(width.dp).fillMaxHeight()) { DrivingApp() }
            }
        }
    }
    delay(1_000)
    return UiTraversal(test, "${width}dp-${fontScale}font").run()
}
