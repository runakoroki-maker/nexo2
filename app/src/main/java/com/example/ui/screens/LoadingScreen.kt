package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.NexoCyan
import com.example.ui.theme.NexoPink
import com.example.ui.theme.NexoPurple

/**
 * Step 8: NEXO Loading Screen
 * Uses the supplied 3-character artwork with dynamic real UI progress bar (0% -> 100%)
 */
@Composable
fun LoadingScreen(
    progressPercent: Int,
    statusMessage: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("nexo_loading_screen")
    ) {
        // Supplied Artwork: Three Original Characters (Female center, Two males on sides)
        Image(
            painter = painterResource(id = R.drawable.nexo_loading_bg),
            contentDescription = "NEXO Trio Characters",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay at bottom for HUD readability
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xCC0E0C16),
                            Color(0xF00A0910)
                        )
                    )
                )
        )

        // HUD Bottom Loading Bar Container
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Loading Label & Percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusMessage.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("loading_status_label")
                )

                Text(
                    text = "$progressPercent%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = NexoPink,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.testTag("loading_percentage_label")
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // High-Tech Cyber Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF201D2C))
                    .border(1.dp, Color(0xFF38334D), RoundedCornerShape(6.dp))
            ) {
                LinearProgressIndicator(
                    progress = { (progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(6.dp))
                        .testTag("loading_progress_bar"),
                    color = NexoPink,
                    trackColor = Color.Transparent
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "v2.0 – BASE APK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8E899E)
                )
                Text(
                    text = "RK ANIMATION STUDIOS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF8E899E),
                    letterSpacing = 1.sp
                )
            }
        }
    }
}
