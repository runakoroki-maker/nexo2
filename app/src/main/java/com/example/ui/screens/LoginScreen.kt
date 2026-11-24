package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.data.network.GeoResult
import com.example.ui.theme.NexoPink
import com.example.ui.theme.NexoPinkDark
import com.example.ui.theme.NexoPurple

/**
 * Main Login Screen:
 * Preserves supplied armed characters battlefield artwork and placeholder login buttons,
 * with all region simulation and QA testing menus completely removed.
 */
@Composable
fun LoginScreen(
    geoResult: GeoResult?,
    onLoginAction: (String) -> Unit,
    onUiClick: () -> Unit,
    toastMessage: String?,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("nexo_main_login_screen")
    ) {
        // Supplied Artwork: Armed trio on futuristic battlefield in landscape
        Image(
            painter = painterResource(id = R.drawable.nexo_login_bg),
            contentDescription = "NEXO Battle Royale Armed Trio",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Top Status HUD Bar
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Version and Live Verified Region Badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99100E19))
                    .border(1.dp, NexoPink.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00E676))
                )
                Spacer(modifier = Modifier.width(8.dp))
                val countryLabel = geoResult?.countryCode ?: "--"
                Text(
                    text = "SERVER ONLINE • REGION: $countryLabel • v2.0 BASE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )
            }

            // More Options (Info & Privacy Policy only — zero simulation or QA controls)
            Box {
                IconButton(
                    onClick = {
                        onUiClick()
                        showMoreMenu = true
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x99100E19))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape)
                        .testTag("login_more_options_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Privacy Policy (IP Geolocation)") },
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.ic_policy), contentDescription = null) },
                        onClick = {
                            onUiClick()
                            showMoreMenu = false
                            showPrivacyDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("About NEXO & RK Studios") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = {
                            onUiClick()
                            showMoreMenu = false
                            showAboutDialog = true
                        }
                    )
                }
            }
        }

        // Bottom Login Floating Action HUD
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Toast Notification Banner (When Login Buttons Tapped)
            AnimatedVisibility(
                visible = toastMessage != null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (toastMessage != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NexoPinkDark),
                        modifier = Modifier
                            .padding(bottom = 12.dp)
                            .testTag("coming_soon_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = toastMessage,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            TextButton(
                                onClick = onDismissToast,
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text("OK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Styled Login Buttons Row
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xD912101B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(NexoPink, NexoPurple)),
                        shape = RoundedCornerShape(22.dp)
                    )
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. Login with Google (Placeholder)
                    Button(
                        onClick = { onLoginAction("Google Login") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = Color(0xFF1F1F1F)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 6.dp)
                            .testTag("google_login_button")
                    ) {
                        Text(
                            text = "G  Google",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // 2. Login with Facebook (Placeholder)
                    Button(
                        onClick = { onLoginAction("Facebook Login") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF1877F2),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 6.dp)
                            .testTag("facebook_login_button")
                    ) {
                        Text(
                            text = "f  Facebook",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // 3. Guest Login (Placeholder)
                    Button(
                        onClick = { onLoginAction("Guest Login") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NexoPink,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .padding(horizontal = 6.dp)
                            .testTag("guest_login_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Guest",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Text("Privacy & IP Geolocation Policy", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "NEXO v2.0 Base APK verifies regional network routing using public IP geolocation to ensure testing availability in designated launch countries (Netherlands and Indonesia).\n\n" +
                    "• No GPS, Wi-Fi location, SIM country, or physical device location is collected or requested.\n" +
                    "• No IP addresses are stored or shared.\n" +
                    "• Verification is performed via secure HTTPS routing endpoints during startup.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUiClick()
                        showPrivacyDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoPink)
                ) {
                    Text("Understood")
                }
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text("NEXO Battle Royale", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Developer: RK Animation Studios\n" +
                    "Game Name: NEXO\n" +
                    "Category: Battle Royale\n" +
                    "Version: 2.0 – Base Build\n" +
                    "Orientation: Landscape Only\n" +
                    "Initial Testing Regions: Netherlands (NL) & Indonesia (ID)\n\n" +
                    "Notice: Authentication services, multiplayer matchmaking, and map gameplay will be introduced in subsequent updates.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUiClick()
                        showAboutDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexoPink)
                ) {
                    Text("Close")
                }
            }
        )
    }
}
