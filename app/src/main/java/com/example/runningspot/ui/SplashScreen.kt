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
import androidx.compose.runtime.remember
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
fun SplashScreen(onFinished: () -> Unit) {
	val alpha = remember { Animatable(0f) }

	LaunchedEffect(Unit) {
		alpha.animateTo(1f, animationSpec = tween(durationMillis = 500))
		kotlinx.coroutines.delay(450)
		alpha.animateTo(0f, animationSpec = tween(durationMillis = 450))
		onFinished()
	}

	Surface(
		modifier = Modifier.fillMaxSize(),
		color = MaterialTheme.colorScheme.background
	) {
		Box(modifier = Modifier.fillMaxSize()) {
			AppTopLogo(
				modifier = Modifier
					.align(Alignment.Center)
					.alpha(alpha.value),
				size = splashCenterLogoSize
			)
		}
	}
}
