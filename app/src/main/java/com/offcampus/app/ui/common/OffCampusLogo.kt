package com.offcampus.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas

/**
 * Brand icon tile for OffCampus: Lloyds Deep Forest Green (#005A36) badge
 * with outbound campus mobility transit loop and pooled passenger nodes.
 */
@Composable
fun OffCampusLogoIcon(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Color(0xFF005A36)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            val w = this.size.width
            val h = this.size.height
            val strokeW = w * 0.085f
            val strokeStyle = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)

            // Scale factor relative to 108 viewport
            fun sx(x: Float) = (x / 108f) * w
            fun sy(y: Float) = (y / 108f) * h

            // 1. Campus Outbound Transit Route Ring (White arc)
            val ringPath = Path().apply {
                moveTo(sx(52f), sy(75f))
                cubicTo(
                    sx(40.4f), sy(75f),
                    sx(31f), sy(65.6f),
                    sx(31f), sy(54f)
                )
                cubicTo(
                    sx(31f), sy(42.4f),
                    sx(40.4f), sy(33f),
                    sx(52f), sy(33f)
                )
                cubicTo(
                    sx(60f), sy(33f),
                    sx(67f), sy(37.5f),
                    sx(70.5f), sy(44f)
                )
            }
            drawPath(path = ringPath, color = Color.White, style = strokeStyle)

            // 2. Shared Pooling Arc (Accent Green #4ADE80)
            val poolPath = Path().apply {
                moveTo(sx(72.5f), sy(54f))
                cubicTo(
                    sx(72.5f), sy(64f),
                    sx(64.5f), sy(73.5f),
                    sx(53f), sy(74.8f)
                )
            }
            drawPath(
                path = poolPath,
                color = Color(0xFF4ADE80),
                style = Stroke(width = strokeW * 0.9f, cap = StrokeCap.Round)
            )

            // 3. Outbound Route Arrow Stem (White)
            drawLine(
                color = Color.White,
                start = Offset(sx(54f), sy(54f)),
                end = Offset(sx(74f), sy(34f)),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )

            // 4. Outbound Route Arrow Head (White)
            val arrowHead = Path().apply {
                moveTo(sx(60f), sy(33f))
                lineTo(sx(75f), sy(33f))
                lineTo(sx(75f), sy(48f))
            }
            drawPath(path = arrowHead, color = Color.White, style = strokeStyle)

            // 5. Waypoint Nodes
            drawCircle(
                color = Color.White,
                radius = sx(3.5f),
                center = Offset(sx(44f), sy(54f))
            )
            drawCircle(
                color = Color.White,
                radius = sx(3.5f),
                center = Offset(sx(54f), sy(45f))
            )
            drawCircle(
                color = Color(0xFF4ADE80),
                radius = sx(3.5f),
                center = Offset(sx(64f), sy(54f))
            )
        }
    }
}

/**
 * Full branded header displaying the OffCampus logo mark and typography.
 */
@Composable
fun OffCampusBrandHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        OffCampusLogoIcon(size = 52.dp)
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "OffCampus",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Campus Ride-Pooling",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
