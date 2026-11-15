package com.thrive.pleos.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import com.thrive.pleos.R

@Composable
fun ThriveBackdrop(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().clipToBounds().background(ThriveColors.Background)) {
        Image(painterResource(R.drawable.home_background_base), null,
            Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = .20f)))
        // Figma 104:17094: the wave is 1824 wide at x=-93 in a 1700-wide app frame.
        Box(Modifier.matchParentSize().layout { measurable, constraints ->
            val width = (constraints.maxWidth * 1824f / 1700f).toInt()
            val artwork = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                artwork.place(-(constraints.maxWidth * 93f / 1700f).toInt(), 0)
            }
        }) {
            Image(painterResource(R.drawable.home_background_wave), null,
                Modifier.matchParentSize(), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = .20f)))
        }
        content()
    }
}

@Composable
fun ThriveGlassPanel(modifier: Modifier = Modifier, shadowElevation: Dp = 0.dp, content: @Composable () -> Unit) {
    // Paint the fill once; optional shadow is outside the card, with no interior Surface layer.
    CompositionLocalProvider(LocalContentColor provides ThriveColors.Text) {
        Box(modifier.shadow(shadowElevation, ThriveShape.Panel, clip = false,
            ambientColor = ThriveColors.Brand.copy(alpha = .08f), spotColor = ThriveColors.Brand.copy(alpha = .10f))
            .clip(ThriveShape.Panel).background(ThriveColors.ReadingSurface)) { content() }
    }
}
