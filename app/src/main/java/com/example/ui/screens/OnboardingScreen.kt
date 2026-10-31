package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.example.R
import com.example.ui.theme.MileageBlue
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

private data class OnboardingPage(
    val kicker: String,
    val headline: String,
    val body: String,
    @param:DrawableRes val art: Int,
    val artDescription: String
)

private val pages = listOf(
    OnboardingPage(
        kicker = "Welcome to",
        headline = "MileageTracker",
        body = "Welcome to MileageTracker, your guide to quick and reliable mileage tracking. Easily log the right trip, track its distance, and keep it ready for tax time.",
        art = R.drawable.onboarding_welcome,
        artDescription = "Car"
    ),
    OnboardingPage(
        kicker = "Quick and Reliable",
        headline = "Live Tracking",
        body = "Live tracking records the route as you drive, so business and personal miles stay accurate. Start a drive, follow the distance, and save the trip when you stop.",
        art = R.drawable.onboarding_live,
        artDescription = "Live location"
    ),
    OnboardingPage(
        kicker = "Effortless",
        headline = "Tax Reports",
        body = "Scan a gas receipt, classify the drive, and turn your trips into a deduction report. See what you spent and what you can claim.",
        art = R.drawable.onboarding_tax,
        artDescription = "Tax report"
    )
)

private val Ink = Color(0xFF1E293B)
private val DotIdle = Color(0xFFD5DCE6)

/**
 * Three swipeable intro screens shown once, after the landing page.
 * Skip and the last Continue both finish into the action the user already chose.
 */
@Composable
fun OnboardingScreen(
    onBackToLanding: () -> Unit,
    onDone: () -> Unit
) {
    var page by rememberSaveable { mutableIntStateOf(0) }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as Activity).window
        val insets = WindowCompat.getInsetsController(window, view)
        val previous = insets.isAppearanceLightStatusBars
        insets.isAppearanceLightStatusBars = true
        onDispose { insets.isAppearanceLightStatusBars = previous }
    }

    BackHandler {
        if (page == 0) onBackToLanding() else page -= 1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("onboarding_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (page > 0) {
                IconButton(
                    onClick = { page -= 1 },
                    modifier = Modifier.testTag("onboarding_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = Ink,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(48.dp))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDone,
                modifier = Modifier.testTag("onboarding_skip")
            ) {
                Text(
                    text = "Skip",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF94A3B8)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .pointerInput(page) {
                    var drag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = {
                            when {
                                drag < -48f && page < pages.lastIndex -> page += 1
                                drag > 48f && page > 0 -> page -= 1
                            }
                        }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            OnboardingArtCard(
                page = pages[page],
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = pages[page].kicker,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Text(
                text = pages[page].headline,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = pages[page].body,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.weight(1f))
        }

        TextButton(
            onClick = {
                if (page == pages.lastIndex) onDone() else page += 1
            },
            modifier = Modifier.testTag("onboarding_next")
        ) {
            Text(
                text = if (page == pages.lastIndex) "Continue" else "Next",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MileageBlue
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp)
        ) {
            repeat(pages.size) { index ->
                val selected = index == page
                Box(
                    modifier = Modifier
                        .height(7.dp)
                        .width(if (selected) 18.dp else 7.dp)
                        .background(if (selected) MileageBlue else DotIdle, CircleShape)
                )
            }
        }
    }
}

@Composable
private fun OnboardingArtCard(page: OnboardingPage, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(
        topStartPercent = 0,
        topEndPercent = 0,
        bottomStartPercent = 50,
        bottomEndPercent = 50
    )
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val cardHeight = (maxWidth * 1.2f).coerceAtMost((maxHeight - 176.dp).coerceAtLeast(1.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = Color(0x400F172A),
                    spotColor = Color(0x400F172A)
                )
                .clip(shape)
                .background(MileageBlue)
        ) {
            Image(
                painter = painterResource(page.art),
                contentDescription = page.artDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
