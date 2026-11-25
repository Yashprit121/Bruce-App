package bruce.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --- Orion-Store Inspired Palette ---
val OrionOledBg = Color(0xFF000000)
val OrionSurfaceLow = Color(0xFF0D0E12)
val OrionSurfaceCard = Color(0xFF16181F)
val OrionPrimaryNeon = Color(0xFF4F46E5)      // Electric Indigo
val OrionAccentLime = Color(0xFFD4E743)       // Acid Lime accent
val OrionStatusGreen = Color(0xFF10B981)      // Connected Emerald
val OrionTextMuted = Color(0xFF8E95A5)

/**
 * Spring-squash interactive button inspired by Orion-Store's `springClick`
 */
@Composable
fun OrionButton(
    modifier: Modifier = Modifier,
    label: String,
    subLabel: String? = null,
    icon: ImageVector,
    containerColor: Color = OrionSurfaceCard,
    contentColor: Color = Color.White,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "OrionButtonSpring"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(containerColor)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)), shape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(vertical = 16.dp, horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = label, tint = contentColor, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subLabel != null) {
                    Text(
                        text = subLabel,
                        color = OrionTextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/**
 * Modern Orion-Styled Screen Mirroring & Navigator View
 */
@Composable
fun OrionBruceMirrorView(
    isConnected: Boolean,
    deviceModel: String = "M5StickC Plus 2",
    fps: Int = 24,
    onButtonAPress: () -> Unit,
    onButtonBPress: () -> Unit,
    onPowerPress: () -> Unit,
    mirrorContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OrionOledBg)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- 1. Orion Glassmorphic Header & Status Chips ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(OrionSurfaceLow)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulsing Status Dot
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isConnected) OrionStatusGreen else Color.Red)
                )
                Column {
                    Text(
                        text = deviceModel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (isConnected) "USB OTG Active (COM8)" else "Disconnected",
                        color = OrionTextMuted,
                        fontSize = 11.sp
                    )
                }
            }

            // Live Telemetry Chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(OrionSurfaceCard)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "$fps FPS",
                    color = OrionAccentLime,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // --- 2. Screen Mirror Bezel (OLED Card Container) ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(OrionSurfaceLow)
                .border(
                    1.dp,
                    Brush.radialGradient(
                        colors = listOf(OrionPrimaryNeon.copy(alpha = 0.35f), Color.Transparent),
                        radius = 800f
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            // Screen mirror canvas rendered here
            mirrorContent()
        }

        Spacer(modifier = Modifier.height(20.dp))

        // --- 3. Orion Segmented Controller Group ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "CONTROLLER (M5 NAVIGATION)",
                color = OrionTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 6.dp)
            )

            // Primary Action Buttons in Segmented Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button A (Big Front / Enter)
                OrionButton(
                    modifier = Modifier.weight(1.3f),
                    label = "SELECT",
                    subLabel = "Btn A / M5",
                    icon = Icons.Rounded.CheckCircle,
                    containerColor = OrionPrimaryNeon,
                    contentColor = Color.White,
                    onClick = onButtonAPress
                )

                // Button B (Top / Next)
                OrionButton(
                    modifier = Modifier.weight(1.1f),
                    label = "NEXT",
                    subLabel = "Btn B",
                    icon = Icons.Rounded.PlayArrow,
                    containerColor = OrionSurfaceCard,
                    contentColor = Color.White,
                    onClick = onButtonBPress
                )

                // Power Button (Back)
                OrionButton(
                    modifier = Modifier.weight(1.0f),
                    label = "BACK",
                    subLabel = "Power",
                    icon = Icons.Rounded.ArrowBack,
                    containerColor = OrionSurfaceCard,
                    contentColor = OrionTextMuted,
                    onClick = onPowerPress
                )
            }
        }
    }
}