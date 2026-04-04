package com.example.runningspot.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.runningspot.R

private val topLogoSize = 156.dp
private val splashCenterLogoSize = 260.dp

@Composable
fun AppTopLogo(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = topLogoSize
) {
    Image(
        painter = painterResource(id = R.drawable.app_logo),
        contentDescription = "RunningSpot logo",
        modifier = modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

@Composable
fun SplashScreen(
    isReady: Boolean,
    onFinished: () -> Unit
) {
    val alpha = remember { Animatable(0f) }
    var minDisplayDone by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(500))
        kotlinx.coroutines.delay(450)
        minDisplayDone = true
    }

    LaunchedEffect(isReady, minDisplayDone) {
        if (isReady && minDisplayDone && !finished) {
            finished = true
            alpha.animateTo(0f, animationSpec = tween(300))
            onFinished()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.app_logo),
                contentDescription = "RunningSpot Logo",
                modifier = Modifier
                    .size(splashCenterLogoSize)
                    .alpha(alpha.value),
                contentScale = ContentScale.Fit
            )
        }
    }
}
