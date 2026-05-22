package com.example.domain

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Vector3D(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vector3D) = Vector3D(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3D) = Vector3D(x - other.x, y - other.y, z - other.z)
    operator fun times(factor: Float) = Vector3D(x * factor, y * factor, z * factor)
    
    fun distanceTo(other: Vector3D): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return sqrt(dx * dx + dy * dy + dz * dz)
    }
}

enum class ObstacleType {
    CONE,
    BOX,
    BARRIER,
    POTHOLE,
    MOVING_DRUM
}

data class Obstacle(
    val id: Int,
    var position: Vector3D,
    val type: ObstacleType,
    val size: Float,
    var active: Boolean = true,
    val isMoving: Boolean = false,
    val moveRange: Float = 0f,
    val moveSpeed: Float = 1.5f,
    val startX: Float = 0f,
    var moveDir: Float = 1f
)

enum class CollectibleType {
    COIN,
    BOOST
}

data class Collectible(
    val id: Int,
    val position: Vector3D,
    val type: CollectibleType,
    val size: Float,
    var active: Boolean = true,
    var rotation: Float = 0f
)

data class Checkpoint(
    val id: Int,
    val position: Vector3D,
    val radius: Float,
    var active: Boolean = true
)

data class Particle(
    var position: Vector3D,
    val velocity: Vector3D,
    val color: Long,
    var life: Float,
    val maxLife: Float
)

data class LevelConfig(
    val level: Int,
    val name: String,
    val description: String,
    val targetZ: Float,
    val totalTime: Int,
    val checkpoints: List<Checkpoint>,
    val obstacles: List<Obstacle>,
    val collectibles: List<Collectible>
)

class CarState {
    var position = Vector3D(0f, 0f, 0f)
    var yaw = 0f // Yaw angle in radians (0 is straight along +Z axis)
    var speed = 0f
    var steerAngle = 0f // Current steer angle in radians
    var health = 100f
    
    // Smooth camera target
    var camX = 0f
    var camY = 2.5f
    var camZ = -5f
    
    // Physics constants
    val maxSpeed = 22f
    val maxBackSpeed = -8f
    val acceleration = 12f
    val braking = 25f
    val friction = 3f
    val steerSpeed = 3.5f
    val maxSteerAngle = 0.5f // ~30 degrees
    
    fun update(
        dt: Float,
        accelPressed: Boolean,
        brakePressed: Boolean,
        steerLeft: Boolean,
        steerRight: Boolean
    ) {
        // 1. Steering inputs
        val targetSteer = when {
            steerLeft -> -maxSteerAngle
            steerRight -> maxSteerAngle
            else -> 0f
        }
        
        // Interpolate steering for smoothness
        val steerDiff = targetSteer - steerAngle
        steerAngle += steerDiff * steerSpeed * dt
        
        // 2. Speed update
        if (accelPressed) {
            speed += acceleration * dt
            if (speed > maxSpeed) speed = maxSpeed
        } else if (brakePressed) {
            speed -= braking * dt
            if (speed < maxBackSpeed) speed = maxBackSpeed
        } else {
            // Apply friction
            if (speed > 0) {
                speed -= friction * dt
                if (speed < 0) speed = 0f
            } else if (speed < 0) {
                speed += friction * dt
                if (speed > 0) speed = 0f
            }
        }
        
        // Steering affects yaw based on current speed (car must move to turn)
        if (speed != 0f) {
            val turnFactor = if (speed > 0) 1f else -1f
            // Higher speed = slightly wider turns, low speed = tighter
            val turnMultiplier = turnFactor * (0.5f + 0.5f * (1f - (speed / maxSpeed).coerceIn(0f, 0.8f)))
            yaw += steerAngle * speed * 0.15f * turnMultiplier * dt
        }
        
        // 3. Move car position in 3D
        // Forward is driving in Z/X plane using yaw:
        // dx = speed * sin(yaw)
        // dz = speed * cos(yaw)
        val dx = speed * sin(yaw) * dt
        val dz = speed * cos(yaw) * dt
        position = Vector3D(position.x + dx, position.y, position.z + dz)
        
        // Clamp boundaries to keep car on track (e.g., width of 8 meters)
        val halfTrackWidth = 6.5f
        if (position.x < -halfTrackWidth) {
            position = Vector3D(-halfTrackWidth, position.y, position.z)
            speed *= -0.2f // bounce slightly
        } else if (position.x > halfTrackWidth) {
            position = Vector3D(halfTrackWidth, position.y, position.z)
            speed *= -0.2f // bounce slightly
        }
        
        // 4. Update camera follow position (Lagging smooth filter for high stability)
        // Camera target height is 2.2m from floor, 5.5m behind the car.
        val targetCamZ = position.z - cos(yaw) * 4.8f
        val targetCamX = position.x - sin(yaw) * 4.8f
        val targetCamY = position.y + 2.4f
        
        val camLerp = 7.5f * dt
        camX += (targetCamX - camX) * camLerp
        camY += (targetCamY - camY) * camLerp
        camZ += (targetCamZ - camZ) * camLerp
    }
    
    fun bounceBack() {
        speed = -speed * 0.4f
    }
}

object PredefinedLevels {
    fun createLevel(levelNum: Int): LevelConfig {
        return when (levelNum) {
            1 -> LevelConfig(
                level = 1,
                name = "Aero Neon Grid",
                description = "Scan surface and drive! Collect all coins and reach the neon glowing gate.",
                targetZ = 130f,
                totalTime = 40,
                checkpoints = listOf(
                    Checkpoint(1, Vector3D(0f, 0f, 40f), 2.5f),
                    Checkpoint(2, Vector3D(2f, 0f, 85f), 2.5f),
                    Checkpoint(3, Vector3D(-1f, 0f, 125f), 3.0f)
                ),
                obstacles = listOf(
                    Obstacle(1, Vector3D(-1.5f, 0f, 20f), ObstacleType.CONE, 0.6f),
                    Obstacle(2, Vector3D(1.5f, 0f, 20f), ObstacleType.CONE, 0.6f),
                    Obstacle(3, Vector3D(3.0f, 0f, 55f), ObstacleType.BOX, 1.0f),
                    Obstacle(4, Vector3D(-2.5f, 0f, 70f), ObstacleType.BARRIER, 1.4f),
                    Obstacle(5, Vector3D(0f, 0f, 105f), ObstacleType.CONE, 0.6f),
                    Obstacle(6, Vector3D(2.5f, 0f, 112f), ObstacleType.CONE, 0.6f)
                ),
                collectibles = listOf(
                    Collectible(1, Vector3D(0f, 0.2f, 15f), CollectibleType.COIN, 0.5f),
                    Collectible(2, Vector3D(-2f, 0.2f, 30f), CollectibleType.COIN, 0.5f),
                    Collectible(3, Vector3D(2f, 0.2f, 40f), CollectibleType.COIN, 0.5f),
                    Collectible(4, Vector3D(0f, 0.2f, 65f), CollectibleType.BOOST, 0.6f),
                    Collectible(5, Vector3D(1.5f, 0.4f, 100f), CollectibleType.COIN, 0.5f),
                    Collectible(6, Vector3D(-2.5f, 0.4f, 115f), CollectibleType.COIN, 0.5f)
                )
            )
            2 -> LevelConfig(
                level = 2,
                name = "Cyber Dodge Maze",
                description = "Avoid barriers and moving hazards to hit precision checkpoints.",
                targetZ = 180f,
                totalTime = 50,
                checkpoints = listOf(
                    Checkpoint(1, Vector3D(-3f, 0f, 45f), 2.5f),
                    Checkpoint(2, Vector3D(3f, 0f, 95f), 2.5f),
                    Checkpoint(3, Vector3D(-2f, 0f, 140f), 2.5f),
                    Checkpoint(4, Vector3D(0f, 0f, 175f), 3.0f)
                ),
                obstacles = listOf(
                    // Moving objects & potholes
                    Obstacle(1, Vector3D(0f, -0.01f, 25f), ObstacleType.POTHOLE, 1.5f),
                    Obstacle(2, Vector3D(-1.5f, 0f, 35f), ObstacleType.CONE, 0.6f),
                    Obstacle(3, Vector3D(1.5f, 0f, 35f), ObstacleType.CONE, 0.6f),
                    // Moving obstacle
                    Obstacle(4, Vector3D(-2f, 0f, 65f), ObstacleType.MOVING_DRUM, 0.9f, isMoving = true, moveRange = 4.5f, startX = -2f, moveSpeed = 2.4f),
                    Obstacle(5, Vector3D(3f, 0f, 80f), ObstacleType.BOX, 1.0f),
                    Obstacle(6, Vector3D(-2.5f, 0f, 85f), ObstacleType.BOX, 1.0f),
                    Obstacle(7, Vector3D(0f, 0f, 115f), ObstacleType.BARRIER, 1.4f),
                    // Second moving obstacle
                    Obstacle(8, Vector3D(2f, 0f, 125f), ObstacleType.MOVING_DRUM, 0.9f, isMoving = true, moveRange = 5.0f, startX = 2f, moveSpeed = 2.8f),
                    Obstacle(9, Vector3D(-3.5f, -0.01f, 155f), ObstacleType.POTHOLE, 1.6f),
                    Obstacle(10, Vector3D(3.5f, -0.01f, 160f), ObstacleType.POTHOLE, 1.6f),
                    Obstacle(11, Vector3D(0f, 0f, 165f), ObstacleType.CONE, 0.6f)
                ),
                collectibles = listOf(
                    Collectible(1, Vector3D(-2f, 0.2f, 20f), CollectibleType.COIN, 0.5f),
                    Collectible(2, Vector3D(2f, 0.2f, 20f), CollectibleType.COIN, 0.5f),
                    Collectible(3, Vector3D(1.5f, 0.2f, 50f), CollectibleType.BOOST, 0.6f),
                    Collectible(4, Vector3D(-3f, 0.2f, 80f), CollectibleType.COIN, 0.5f),
                    Collectible(5, Vector3D(3f, 0.2f, 110f), CollectibleType.COIN, 0.5f),
                    Collectible(6, Vector3D(0f, 0.2f, 135f), CollectibleType.COIN, 0.5f),
                    Collectible(7, Vector3D(-1.5f, 0.2f, 165f), CollectibleType.BOOST, 0.6f)
                )
            )
            else -> LevelConfig(
                level = 3,
                name = "Omega Grand Circuit",
                description = "The ultimate test! Extreme narrow floor space, rapid speed, and heavy crashes.",
                targetZ = 240f,
                totalTime = 45,
                checkpoints = listOf(
                    Checkpoint(1, Vector3D(2f, 0f, 50f), 2.5f),
                    Checkpoint(2, Vector3D(-2f, 0f, 100f), 2.5f),
                    Checkpoint(3, Vector3D(3f, 0f, 150f), 2.5f),
                    Checkpoint(4, Vector3D(-3f, 0f, 200f), 2.5f),
                    Checkpoint(5, Vector3D(0f, 0f, 235f), 3.0f)
                ),
                obstacles = listOf(
                    Obstacle(1, Vector3D(0f, 0f, 15f), ObstacleType.BARRIER, 1.4f),
                    Obstacle(2, Vector3D(-3f, 0f, 35f), ObstacleType.BOX, 1.0f),
                    Obstacle(3, Vector3D(3f, 0f, 40f), ObstacleType.BOX, 1.0f),
                    // High speeds moving sweepers
                    Obstacle(4, Vector3D(-3f, 0f, 75f), ObstacleType.MOVING_DRUM, 1.0f, isMoving = true, moveRange = 6.0f, startX = -3f, moveSpeed = 3.8f),
                    Obstacle(5, Vector3D(0f, -0.01f, 90f), ObstacleType.POTHOLE, 2.0f),
                    Obstacle(6, Vector3D(3f, 0f, 120f), ObstacleType.BARRIER, 1.4f),
                    Obstacle(7, Vector3D(-3f, 0f, 122f), ObstacleType.BARRIER, 1.4f),
                    // Double sweeper crossing
                    Obstacle(8, Vector3D(-2.5f, 0f, 160f), ObstacleType.MOVING_DRUM, 0.9f, isMoving = true, moveRange = 5.0f, startX = -2.5f, moveSpeed = 4.0f),
                    Obstacle(9, Vector3D(2.5f, 0f, 175f), ObstacleType.MOVING_DRUM, 0.9f, isMoving = true, moveRange = 5.0f, startX = 2.5f, moveSpeed = -3.5f),
                    Obstacle(10, Vector3D(0f, -0.01f, 190f), ObstacleType.POTHOLE, 1.8f),
                    Obstacle(11, Vector3D(-2f, 0f, 215f), ObstacleType.BOX, 1.0f),
                    Obstacle(12, Vector3D(2f, 0f, 215f), ObstacleType.BOX, 1.0f),
                    Obstacle(13, Vector3D(0f, 0f, 222f), ObstacleType.CONE, 0.6f)
                ),
                collectibles = listOf(
                    Collectible(1, Vector3D(2f, 0.2f, 20f), CollectibleType.BOOST, 0.6f),
                    Collectible(2, Vector3D(-2f, 0.2f, 45f), CollectibleType.COIN, 0.5f),
                    Collectible(3, Vector3D(3f, 0.2f, 85f), CollectibleType.COIN, 0.5f),
                    Collectible(4, Vector3D(0f, 0.2f, 110f), CollectibleType.BOOST, 0.6f),
                    Collectible(5, Vector3D(-3f, 0.2f, 135f), CollectibleType.COIN, 0.5f),
                    Collectible(6, Vector3D(3f, 0.2f, 185f), CollectibleType.COIN, 0.5f),
                    Collectible(7, Vector3D(0f, 0.2f, 208f), CollectibleType.BOOST, 0.6f),
                    Collectible(8, Vector3D(0f, 0.2f, 230f), CollectibleType.COIN, 0.5f)
                )
            )
        }
    }
}
