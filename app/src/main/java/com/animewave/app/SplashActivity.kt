package com.animewave.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class SplashActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFF090A12)) {
                    val scale = remember { Animatable(0.72f) }
                    LaunchedEffect(Unit) {
                        scale.animateTo(1f, animationSpec = tween(650))
                        delay(850)
                        startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                        finish()
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                    }
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.radialGradient(listOf(Color(0xFF342052), Color(0xFF090A12)))
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Box(
                                modifier = Modifier.size(100.dp).scale(scale.value)
                                    .background(Brush.linearGradient(listOf(Color(0xFF9B72FF), Color(0xFFFF6DAE))), RoundedCornerShape(30.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.Black)
                            }
                            Spacer(Modifier.height(22.dp))
                            Text("AnimeWave", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.height(8.dp))
                            Text("YOUR ANIME UNIVERSE", color = Color(0xFFB7A5E8), fontSize = 10.sp, letterSpacing = 3.sp)
                            Spacer(Modifier.height(30.dp))
                            Text("Created with passion • Malawi 🇲🇼", color = Color(0xFFAAAFC3), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}
