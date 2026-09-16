package com.leadmilers.saathi.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leadmilers.saathi.R
import kotlinx.coroutines.delay

private val Coral    = Color(0xFFE98B8B)
private val PlumMid  = Color(0xFF5B3A68)

@Composable
fun SplashContent(onReady: () -> Unit) {
    val alpha     = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.92f) }

    LaunchedEffect(Unit) {
        alpha.animateTo(1f, tween(600, easing = EaseOutCubic))
        scaleAnim.animateTo(1f, tween(500, easing = EaseOutBack))
        delay(900)
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier
                .alpha(alpha.value)
                .scale(scaleAnim.value)
        ) {
            Image(
                painter            = painterResource(R.drawable.saathi_logo),
                contentDescription = "Saathi",
                modifier           = Modifier
                    .width(220.dp)
                    .wrapContentHeight()
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Your health companion",
                color         = Coral.copy(alpha = 0.85f),
                fontSize      = 13.sp,
                fontWeight    = FontWeight.Medium,
                letterSpacing = 1.4.sp,
                textAlign     = TextAlign.Center,
            )
        }

        Text(
            "All data stays on your device",
            color         = PlumMid.copy(alpha = 0.35f),
            fontSize      = 10.sp,
            letterSpacing = 0.4.sp,
            modifier      = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 40.dp)
                .alpha(alpha.value)
        )
    }
}
