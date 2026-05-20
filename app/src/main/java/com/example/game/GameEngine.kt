package com.example.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.setValue
import kotlin.math.cos
import kotlin.math.sin

class Player(
    startX: Double,
    startY: Double,
    startDirX: Double,
    startDirY: Double,
    startPlaneX: Double,
    startPlaneY: Double
) {
    var x by mutableDoubleStateOf(startX)
    var y by mutableDoubleStateOf(startY)
    var dirX by mutableDoubleStateOf(startDirX)
    var dirY by mutableDoubleStateOf(startDirY)
    var planeX by mutableDoubleStateOf(startPlaneX)
    var planeY by mutableDoubleStateOf(startPlaneY)

    private fun isWall(mapX: Int, mapY: Int, worldMap: Array<IntArray>): Boolean {
        if (mapX < 0 || mapX >= worldMap.size || mapY < 0 || mapY >= worldMap[0].size) return true
        return worldMap[mapX][mapY] > 0
    }

    fun moveForward(speed: Double, worldMap: Array<IntArray>) {
        if (!isWall((x + dirX * speed).toInt(), y.toInt(), worldMap)) x += dirX * speed
        if (!isWall(x.toInt(), (y + dirY * speed).toInt(), worldMap)) y += dirY * speed
    }

    fun moveBackward(speed: Double, worldMap: Array<IntArray>) {
        if (!isWall((x - dirX * speed).toInt(), y.toInt(), worldMap)) x -= dirX * speed
        if (!isWall(x.toInt(), (y - dirY * speed).toInt(), worldMap)) y -= dirY * speed
    }

    fun strafeRight(speed: Double, worldMap: Array<IntArray>) {
        val strX = -dirY
        val strY = dirX
        if (!isWall((x + strX * speed).toInt(), y.toInt(), worldMap)) x += strX * speed
        if (!isWall(x.toInt(), (y + strY * speed).toInt(), worldMap)) y += strY * speed
    }

    fun strafeLeft(speed: Double, worldMap: Array<IntArray>) {
        val strX = dirY
        val strY = -dirX
        if (!isWall((x + strX * speed).toInt(), y.toInt(), worldMap)) x += strX * speed
        if (!isWall(x.toInt(), (y + strY * speed).toInt(), worldMap)) y += strY * speed
    }

    fun rotate(speed: Double) {
        val oldDirX = dirX
        dirX = dirX * cos(speed) - dirY * sin(speed)
        dirY = oldDirX * sin(speed) + dirY * cos(speed)
        val oldPlaneX = planeX
        planeX = planeX * cos(speed) - planeY * sin(speed)
        planeY = oldPlaneX * sin(speed) + planeY * cos(speed)
    }
}

fun generateMaze(width: Int, height: Int): Array<IntArray> {
    val map = Array(width) { IntArray(height) { 1 } }
    val dirs = arrayOf(Pair(0, -2), Pair(0, 2), Pair(-2, 0), Pair(2, 0))

    fun carve(x: Int, y: Int) {
        val shuffledDirs = dirs.toMutableList().apply { shuffle() }
        for (dir in shuffledDirs) {
            val nx = x + dir.first
            val ny = y + dir.second
            if (nx in 1 until width - 1 && ny in 1 until height - 1 && map[nx][ny] == 1) {
                map[x + dir.first / 2][y + dir.second / 2] = 0
                map[nx][ny] = 0
                carve(nx, ny)
            }
        }
    }

    map[1][1] = 0
    carve(1, 1)

    // Ensure player start area is clear so they don't spawn in a wall
    map[1][1] = 0
    map[1][2] = 0
    map[2][1] = 0
    map[2][2] = 0

    return map
}
