package com.thrive.pleos.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.thrive.pleos.R

/** Local source artwork, in the same back-to-front order as the Figma frame. */
@Composable
fun ThriveBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds().background(ThriveColors.Brand)) {
        Image(
            painter = painterResource(R.drawable.aurora_wave),
            contentDescription = null,
            modifier = Modifier.matchParentSize().graphicsLayer {
                scaleX = 1.2461f
                scaleY = 1.2468f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                translationX = -size.width * 0.2101f
                translationY = -size.height * 0.1937f
            },
            contentScale = ContentScale.FillBounds,
        )
        Image(
            painter = painterResource(R.drawable.aurora_grain),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        Box(Modifier.matchParentSize().background(ThriveColors.Scrim))
        content()
    }
}

@Composable
fun ThriveGlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = modifier,
        shape = ThriveShape.Panel,
        color = ThriveColors.ReadingSurface,
        contentColor = ThriveColors.Text,
        border = BorderStroke(ThriveSize.Border, ThriveColors.Border),
        content = content,
    )
}
