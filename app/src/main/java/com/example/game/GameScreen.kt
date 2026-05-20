package com.example.game

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun GameScreen() {
    val worldMap = remember { generateMaze(31, 31) }
    val player = remember { Player(1.5, 1.5, -1.0, 0.0, 0.0, 0.66) }
    var frameCount by remember { mutableIntStateOf(0) }

    // Constants for colors
    val ceilingColor = Color(0xFFDCC889) // light off-yellow
    val floorColor = Color(0xFF91825B) // darker murky yellow/brown carpet
    val wallColorBase = Color(0xFFDED085) // Backroom yellow wall
    val wallColorDark = Color(0xFFB5A96A) // Darker variant

    var moveX by remember { mutableStateOf(0f) }
    var moveY by remember { mutableStateOf(0f) }
    var lookX by remember { mutableStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        while (isActive) {
            withFrameNanos { time ->
                if (lastTime == 0L) lastTime = time
                val dt = (time - lastTime) / 1e9
                lastTime = time

                // Movement logic
                val moveSpeed = dt * 3.0 // squares/second
                val rotSpeed = dt * 2.0 // radians/second

                // moveY is forward/backward
                if (moveY < -0.2f) player.moveForward(moveSpeed * abs(moveY), worldMap)
                if (moveY > 0.2f) player.moveBackward(moveSpeed * abs(moveY), worldMap)
                
                // moveX is strafe
                if (moveX > 0.2f) player.strafeRight(moveSpeed * abs(moveX), worldMap)
                if (moveX < -0.2f) player.strafeLeft(moveSpeed * abs(moveX), worldMap)
                
                // lookX is turn
                if (lookX > 0.1f) player.rotate(-rotSpeed * abs(lookX))
                if (lookX < -0.1f) player.rotate(rotSpeed * abs(lookX))

                frameCount++ // force re-render
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        val f = frameCount // Subscribe to frame changes
        val resolution = 4 // How wide each vertical bar is
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width.toInt()
            val h = size.height.toInt()
            if (w == 0 || h == 0) return@Canvas

            // Draw ceiling and floor
            drawRect(color = ceilingColor, size = Size(size.width, size.height / 2))
            drawRect(color = floorColor, topLeft = Offset(0f, size.height / 2), size = Size(size.width, size.height / 2))

            for (x in 0 until w step resolution) {
                // calculate ray position and direction
                val cameraX = 2.0 * x / w.toDouble() - 1.0 // x-coordinate in camera space
                val rayDirX = player.dirX + player.planeX * cameraX
                val rayDirY = player.dirY + player.planeY * cameraX

                // which box of the map we're in
                var mapX = player.x.toInt()
                var mapY = player.y.toInt()

                // length of ray from one x or y-side to next x or y-side
                val deltaDistX = if (rayDirX == 0.0) 1e30 else abs(1.0 / rayDirX)
                val deltaDistY = if (rayDirY == 0.0) 1e30 else abs(1.0 / rayDirY)

                var perpWallDist: Double

                var stepX: Int
                var stepY: Int
                var sideDistX: Double
                var sideDistY: Double

                var hit = 0
                var side = 0

                if (rayDirX < 0) {
                    stepX = -1
                    sideDistX = (player.x - mapX) * deltaDistX
                } else {
                    stepX = 1
                    sideDistX = (mapX + 1.0 - player.x) * deltaDistX
                }
                
                if (rayDirY < 0) {
                    stepY = -1
                    sideDistY = (player.y - mapY) * deltaDistY
                } else {
                    stepY = 1
                    sideDistY = (mapY + 1.0 - player.y) * deltaDistY
                }

                while (hit == 0) {
                    if (sideDistX < sideDistY) {
                        sideDistX += deltaDistX
                        mapX += stepX
                        side = 0
                    } else {
                        sideDistY += deltaDistY
                        mapY += stepY
                        side = 1
                    }
                    if (mapX >= 0 && mapX < worldMap.size && mapY >= 0 && mapY < worldMap[0].size) {
                        if (worldMap[mapX][mapY] > 0) hit = 1
                    } else {
                        hit = 1
                    }
                }

                if (side == 0) perpWallDist = (sideDistX - deltaDistX)
                else perpWallDist = (sideDistY - deltaDistY)

                val lineHeight = (h / perpWallDist).toInt()
                val drawStart = max(0, -lineHeight / 2 + h / 2)
                val drawEnd = min(h - 1, lineHeight / 2 + h / 2)

                var color = if (side == 1) wallColorDark else wallColorBase
                val fogDensity = 12.0
                val distanceRatio = min(1.0, perpWallDist / fogDensity).toFloat()
                
                val finalColor = color.copy(
                    red = color.red * (1 - distanceRatio) + 0.05f * distanceRatio,
                    green = color.green * (1 - distanceRatio) + 0.05f * distanceRatio,
                    blue = color.blue * (1 - distanceRatio) + 0.05f * distanceRatio
                )

                drawLine(
                    color = finalColor,
                    start = Offset(x.toFloat(), drawStart.toFloat()),
                    end = Offset(x.toFloat(), drawEnd.toFloat()),
                    strokeWidth = resolution.toFloat()
                )
            }
        }

        // Vignette & Crosshair overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                        radius = 1200f
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Crosshair
            Box(
                modifier = Modifier
                    .size(4.dp)
                    .background(Color.White.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            )
        }

        // Controls Row - Invisible touch areas
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            InvisibleTouchPad(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onMove = { x, y ->
                    moveX = x
                    moveY = y
                }
            )

            InvisibleTouchPad(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                onMove = { x, _ ->
                    lookX = x
                }
            )
        }
    }
}

@Composable
fun InvisibleTouchPad(modifier: Modifier = Modifier, onMove: (Float, Float) -> Unit) {
    val maxRadiusPx = 150f
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        offset = Offset.Zero
                        onMove(0f, 0f)
                    },
                    onDragCancel = {
                        offset = Offset.Zero
                        onMove(0f, 0f)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val newOffset = offset + dragAmount
                    val distance = hypot(newOffset.x, newOffset.y)
                    
                    if (distance <= maxRadiusPx) {
                        offset = newOffset
                    } else {
                        val ratio = maxRadiusPx / distance
                        offset = Offset(newOffset.x * ratio, newOffset.y * ratio)
                    }
                    
                    onMove(offset.x / maxRadiusPx, offset.y / maxRadiusPx)
                }
            }
    )
}
