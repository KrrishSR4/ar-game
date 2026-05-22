package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.example.data.GameRecord
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.domain.*
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun GameScreen(viewModel: GameViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val gamePhase by viewModel.gamePhase.collectAsState()
    val levelConfig by viewModel.levelConfig.collectAsState()
    val carState by viewModel.carState.collectAsState()
    val score by viewModel.score.collectAsState()
    val coinsCollected by viewModel.coinsCollected.collectAsState()
    val timerSeconds by viewModel.timerSeconds.collectAsState()
    val currentLevel by viewModel.currentLevel.collectAsState()
    val screenShake by viewModel.screenShake.collectAsState()
    val scanningProgress by viewModel.scanningProgress.collectAsState()
    val highScore by viewModel.highScore.collectAsState()
    val recordsList by viewModel.historyList.collectAsState()

    // Handle Camera permission checks
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0C0E14))
    ) {
        // 1. Camera live preview or cosmic cyber space fallback
        if (hasCameraPermission && (gamePhase == GamePhase.PLAYING || gamePhase == GamePhase.AR_SCANNING || gamePhase == GamePhase.PAUSED)) {
            CameraPreviewView(modifier = Modifier.fillMaxSize())
        } else {
            CosmicSpaceFallbackBackground(modifier = Modifier.fillMaxSize(), playing = gamePhase == GamePhase.PLAYING)
        }

        // Dark ambient layout mask over camera context for neon readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x770D0F16),
                            Color(0x220D0F16),
                            Color(0x990D0F16)
                        )
                    )
                )
        )

        // 2. Main AR 3D Rendering Canvas (only active during scanning, playing and paused states)
        if (gamePhase == GamePhase.PLAYING || gamePhase == GamePhase.AR_SCANNING || gamePhase == GamePhase.PAUSED) {
            ARGameCanvas(
                viewModel = viewModel,
                carState = carState,
                levelConfig = levelConfig,
                screenShake = screenShake,
                isScanning = gamePhase == GamePhase.AR_SCANNING,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. UI Phase Overlays
        when (gamePhase) {
            GamePhase.WELCOME -> {
                WelcomeOverlayScreen(
                    currentLevel = currentLevel,
                    highScore = highScore,
                    recordsList = recordsList,
                    onSelectLevel = { viewModel.selectLevel(it) },
                    onStartGame = {
                        if (!hasCameraPermission) {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                        viewModel.startScanning()
                    },
                    onClearRecords = { viewModel.clearAllRecords() }
                )
            }
            GamePhase.AR_SCANNING -> {
                ScanningOverlayScreen(
                    progress = scanningProgress,
                    onStartGame = { viewModel.confirmSurfaceAndStartGame() },
                    onCancel = { viewModel.backToMainMenu() }
                )
            }
            GamePhase.PLAYING -> {
                PlayingHUDOverlay(
                    score = score,
                    coins = coinsCollected,
                    timer = timerSeconds,
                    car = carState,
                    levelConfig = levelConfig,
                    levelNum = currentLevel,
                    onPause = { viewModel.pauseGame() },
                    onPressLeft = { viewModel.steerLeft = it },
                    onPressRight = { viewModel.steerRight = it },
                    onPressAccel = { viewModel.accelPressed = it },
                    onPressBrake = { viewModel.brakePressed = it }
                )
            }
            GamePhase.PAUSED -> {
                PauseMenuOverlay(
                    levelName = levelConfig.name,
                    score = score,
                    onResume = { viewModel.resumeGame() },
                    onRestart = { viewModel.restartGame() },
                    onMainMenu = { viewModel.backToMainMenu() }
                )
            }
            GamePhase.MISSION_COMPLETE -> {
                MissionCompleteOverlay(
                    levelName = levelConfig.name,
                    finalScore = score + timerSeconds * 25,
                    bonus = timerSeconds * 25,
                    coins = coinsCollected,
                    timerUsed = levelConfig.totalTime - timerSeconds,
                    onNextLevel = {
                        val nextLvl = if (currentLevel < 3) currentLevel + 1 else 1
                        viewModel.selectLevel(nextLvl)
                        viewModel.startScanning()
                    },
                    onRestart = { viewModel.restartGame() },
                    onMainMenu = { viewModel.backToMainMenu() }
                )
            }
            GamePhase.GAME_OVER -> {
                GameOverOverlay(
                    score = score,
                    healthRemaining = carState.health,
                    timerRemaining = timerSeconds,
                    onRestart = { viewModel.restartGame() },
                    onMainMenu = { viewModel.backToMainMenu() }
                )
            }
        }
    }
}

// Android CameraX preview composable wrapper
@Composable
fun CameraPreviewView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        },
        modifier = modifier,
        update = { previewView ->
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

// Fallback visual simulation for AR workspace/emulators
@Composable
fun CosmicSpaceFallbackBackground(modifier: Modifier = Modifier, playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "cyber_lines")
    val translationY by transition.animateFloat(
        initialValue = 0f,
        targetValue = 400f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "trans_y"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Fill cosmic background
        drawRect(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF04060A), Color(0xFF0D111A), Color(0xFF131826))
            )
        )

        // Draw ambient stars
        val random = java.util.Random(42)
        for (i in 0 until 40) {
            val sx = random.nextFloat() * w
            val sy = (random.nextFloat() * h + if (playing) translationY * 0.4f else 0f) % h
            val sizeRadius = random.nextFloat() * 1.52f + 1f
            drawCircle(
                color = Color(0x9900E5FF),
                radius = sizeRadius,
                center = Offset(sx, sy)
            )
        }

        // Glowing backdrop grids
        val path = Path()
        val gridDist = 80f
        val offsetTop = if (playing) translationY % gridDist else 0f

        for (y in 0 until (h / gridDist).toInt() + 2) {
            val drawY = y * gridDist + offsetTop
            path.reset()
            path.moveTo(0f, drawY)
            path.lineTo(w, drawY)
            drawPath(
                path = path,
                color = Color(0x3300FFD5),
                style = Stroke(width = 1f)
            )
        }
    }
}

// ------------------------------------------------------------------------
// THE AR 3D RECENT-PERSPECTIVE RENDERING CANVAS
// ------------------------------------------------------------------------
@Composable
fun ARGameCanvas(
    viewModel: GameViewModel,
    carState: CarState,
    levelConfig: LevelConfig,
    screenShake: Float,
    isScanning: Boolean,
    modifier: Modifier = Modifier
) {
    // Rotation wheel visual state
    var wheelAngleTick by remember { mutableStateOf(0f) }
    LaunchedEffect(key1 = carState.speed) {
        while (true) {
            if (carState.speed != 0f) {
                wheelAngleTick = (wheelAngleTick + carState.speed * 1.5f) % 360f
            }
            delay(16)
        }
    }

    Canvas(
        modifier = modifier
            .pointerInput(Unit) {
                detectTapGestures { /* Intercept tap for overlays */ }
            }
    ) {
        val w = size.width
        val h = size.height

        // Apply visual screen vibration shake offset
        val shakeX = if (screenShake > 0f) (sin(System.currentTimeMillis() * 0.1f) * screenShake * 22f).toFloat() else 0f
        val shakeY = if (screenShake > 0f) (cos(System.currentTimeMillis() * 0.12f) * screenShake * 22f).toFloat() else 0f

        val projectionContext = ProjectionContext(
            w = w,
            h = h,
            camX = carState.camX + shakeX * 0.005f,
            camY = carState.camY + shakeY * 0.005f,
            camZ = carState.camZ,
            carYaw = carState.yaw
        )

        if (isScanning) {
            // Under AR Plane Scanner: Render target circular floor grid
            drawScanningVirtualGrid(projectionContext, viewModel.scanningProgress.value)
        } else {
            // Under ACTIVE Driving: Renders track grid border, obstacles, checkpoints, coins, car lines
            drawARRoadGridAndTracks(projectionContext, levelConfig)
            drawPotholesUnderlay(projectionContext, levelConfig)
            drawCheckpointsAndGlows(projectionContext, levelConfig)
            drawCollectibles(projectionContext, levelConfig)
            drawObstacles(projectionContext, levelConfig)
            drawCarProjected(projectionContext, carState, wheelAngleTick)
            drawParticles(projectionContext, viewModel.activeParticles)
        }
    }
}

// Simple Helper class for perspective projection transformations
class ProjectionContext(
    val w: Float,
    val h: Float,
    val camX: Float,
    val camY: Float,
    val camZ: Float,
    val carYaw: Float
) {
    fun project(pt: Vector3D): Offset? {
        // Translation relative to Camera
        val dx = pt.x - camX
        val dy = pt.y - camY
        val dz = pt.z - camZ

        // Rotation around Y Axis (Camera Yaw)
        val alpha = -carYaw
        val cosA = cos(alpha)
        val sinA = sin(alpha)

        val rx = dx * cosA - dz * sinA
        val rz = dx * sinA + dz * cosA
        val ry = dy

        // Cut representation behind lens
        if (rz <= 0.1f) return null

        // Perspective ratio formula
        val fov = 1.35f
        val px = rx * fov / rz
        val py = ry * fov / rz

        // Map to 2D UI screen
        val sx = w / 2f + px * (w / 2f)
        val sy = h / 2f - py * (w / 2f) // keep scale proportionate to height limit

        return Offset(sx, sy)
    }

    // Returns perspective line-work thickness scaling based on depth rz
    fun getStrokeWidth(pt: Vector3D, base: Float): Float {
        val dz = pt.z - camZ
        if (dz <= 0f) return 1f
        return (base / (dz * 0.08f + 1f)).coerceIn(1f, base * 3f)
    }
}

// Draw Grid representation for scanning stage
private fun DrawScope.drawScanningVirtualGrid(pc: ProjectionContext, progress: Float) {
    val centerFloor = Vector3D(0f, 0f, 6.0f)
    val projCenter = pc.project(centerFloor) ?: return
    
    val baseRadius = 4.0f * progress
    val rings = 3
    for (i in 1..rings) {
        val currR = (baseRadius / rings) * i
        val path = Path()
        var pathStarted = false
        
        for (a in 0..360 step 15) {
            val rad = Math.toRadians(a.toDouble()).toFloat()
            val circularPt = Vector3D(currR * cos(rad), 0f, 6.0f + currR * sin(rad))
            val projCircle = pc.project(circularPt)
            if (projCircle != null) {
                if (!pathStarted) {
                    path.moveTo(projCircle.x, projCircle.y)
                    pathStarted = true
                } else {
                    path.lineTo(projCircle.x, projCircle.y)
                }
            }
        }
        if (pathStarted) {
            path.close()
            drawPath(
                path = path,
                color = Color(0xAA00E5FF),
                style = Stroke(width = 2.5f, miter = 4f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f))
            )
        }
    }

    // Outer Reticle ticks
    drawCircle(
        color = Color(0xFF00FFCC),
        radius = 16f * progress,
        center = projCenter
    )
}

// Draws the cyberpunk lane, side mesh, and 3D track boundary indicators
private fun DrawScope.drawARRoadGridAndTracks(pc: ProjectionContext, config: LevelConfig) {
    val maxTrackLength = config.targetZ + 20f
    val halfWidth = 6.5f

    // 1. Alternating safety colored border track segment pins
    val step = 4f
    var i = 0f
    while (i <= maxTrackLength) {
        val leftNode1 = pc.project(Vector3D(-halfWidth, 0f, i))
        val leftNode2 = pc.project(Vector3D(-halfWidth, 0f, i + step))
        val rightNode1 = pc.project(Vector3D(halfWidth, 0f, i))
        val rightNode2 = pc.project(Vector3D(halfWidth, 0f, i + step))

        val isEven = (i / step).toInt() % 2 == 0
        val sideColor = if (isEven) Color(0xFFFF4554) else Color(0xFF00FFD5)
        val strokeWidth = 5f / ((i - pc.camZ) * 0.04f + 1f).coerceIn(1f, 10f)

        if (leftNode1 != null && leftNode2 != null) {
            drawLine(sideColor, leftNode1, leftNode2, strokeWidth = strokeWidth)
        }
        if (rightNode1 != null && rightNode2 != null) {
            drawLine(sideColor, rightNode1, rightNode2, strokeWidth = strokeWidth)
        }

        i += step
    }

    // Draw horizontal speed grid lines projected directly onto ground
    var zG = (pc.camZ - pc.camZ % 6f).coerceAtLeast(0f)
    while (zG < pc.camZ + 75f && zG <= maxTrackLength) {
        val p1 = pc.project(Vector3D(-halfWidth, 0f, zG))
        val p2 = pc.project(Vector3D(halfWidth, 0f, zG))
        val lineThick = 2.2f / ((zG - pc.camZ) * 0.05f + 1f).coerceIn(1f, 10f)

        if (p1 != null && p2 != null) {
            drawLine(
                color = Color(0x3300FFD5),
                start = p1,
                end = p2,
                strokeWidth = lineThick
            )
        }
        zG += 6f
    }
}

// Flat potholes overlay painted directly on road
private fun DrawScope.drawPotholesUnderlay(pc: ProjectionContext, config: LevelConfig) {
    config.obstacles.filter { it.active && it.type == ObstacleType.POTHOLE }.forEach { pot ->
        val segments = 16
        val path = Path()
        var init = false
        val r = pot.size

        for (u in 0..segments) {
            val rad = (u * 2 * Math.PI / segments).toFloat()
            val pt = pot.position + Vector3D(r * cos(rad), -0.01f, r * sin(rad))
            val proj = pc.project(pt)
            if (proj != null) {
                if (!init) {
                    path.moveTo(proj.x, proj.y)
                    init = true
                } else {
                    path.lineTo(proj.x, proj.y)
                }
            }
        }
        if (init) {
            path.close()
            // Crimson shaded inside hole
            drawPath(path, color = Color(0xEE1E0911))
            drawPath(
                path = path,
                color = Color(0xFFFF1744),
                style = Stroke(width = pc.getStrokeWidth(pot.position, 4f))
            )
        }
    }
}

// Glowing checkpoint hoop structures
private fun DrawScope.drawCheckpointsAndGlows(pc: ProjectionContext, config: LevelConfig) {
    config.checkpoints.filter { it.active }.forEach { cp ->
        val radius = cp.radius
        val segments = 20
        val bottomPath = Path()
        var init = false

        // Draw glowing floor base circle
        for (i in 0..segments) {
            val rad = (i * 2 * Math.PI / segments).toFloat()
            val pt = cp.position + Vector3D(radius * cos(rad), 0.0f, radius * sin(rad))
            val proj = pc.project(pt)
            if (proj != null) {
                if (!init) {
                    bottomPath.moveTo(proj.x, proj.y)
                    init = true
                } else {
                    pathLineTo(bottomPath, proj)
                }
            }
        }
        if (init) {
            bottomPath.close()
            drawPath(
                path = bottomPath,
                color = Color(0x1F00FFFF)
            )
            drawPath(
                path = bottomPath,
                color = Color(0xFF00FFFF),
                style = Stroke(width = pc.getStrokeWidth(cp.position, 6f))
            )
        }

        // Draw Left-Right arch Pillars
        val leftBase = cp.position + Vector3D(-radius, 0f, 0f)
        val leftTop = cp.position + Vector3D(-radius, 3.2f, 0f)
        val rightBase = cp.position + Vector3D(radius, 0f, 0f)
        val rightTop = cp.position + Vector3D(radius, 3.2f, 0f)

        val plB = pc.project(leftBase)
        val plT = pc.project(leftTop)
        val prB = pc.project(rightBase)
        val prT = pc.project(rightTop)

        val sw = pc.getStrokeWidth(cp.position, 5f)

        if (plB != null && plT != null) {
            drawLine(Color(0xFF00FFFF), plB, plT, strokeWidth = sw)
        }
        if (prB != null && prT != null) {
            drawLine(Color(0xFF00FFFF), prB, prT, strokeWidth = sw)
        }
        
        // Arch neon bridge connecting them
        if (plT != null && prT != null) {
            drawLine(Color(0xFFFF00D4), plT, prT, strokeWidth = sw)
        }
    }
}

// 3D Spinning Hexagonal Coins and Boost Lightning bolts
private fun DrawScope.drawCollectibles(pc: ProjectionContext, config: LevelConfig) {
    config.collectibles.filter { it.active }.forEach { item ->
        val rad = item.rotation * Math.PI / 180f
        val sizeF = item.size
        
        if (item.type == CollectibleType.COIN) {
            // Render Spinning Gold Hexagonal Coin
            val verticesLocal = listOf(
                Vector3D(sizeF * cos(0f).toFloat(), 0.3f, sizeF * sin(0f).toFloat()),
                Vector3D(sizeF * cos(dpToRad(60)).toFloat(), 0.3f, sizeF * sin(dpToRad(60)).toFloat()),
                Vector3D(sizeF * cos(dpToRad(120)).toFloat(), 0.3f, sizeF * sin(dpToRad(120)).toFloat()),
                Vector3D(sizeF * cos(dpToRad(180)).toFloat(), 0.3f, sizeF * sin(dpToRad(180)).toFloat()),
                Vector3D(sizeF * cos(dpToRad(240)).toFloat(), 0.3f, sizeF * sin(dpToRad(240)).toFloat()),
                Vector3D(sizeF * cos(dpToRad(300)).toFloat(), 0.3f, sizeF * sin(dpToRad(300)).toFloat())
            )

            // Dynamic rotation offset
            val rotatedProjected = verticesLocal.map { pt ->
                val rx = pt.x * cos(rad) - pt.z * sin(rad)
                val rz = pt.x * sin(rad) + pt.z * cos(rad)
                pc.project(item.position + Vector3D(rx.toFloat(), pt.y, rz.toFloat()))
            }

            val path = Path()
            var started = false
            rotatedProjected.forEach { proj ->
                if (proj != null) {
                    if (!started) {
                        path.moveTo(proj.x, proj.y)
                        started = true
                    } else {
                        pathLineTo(path, proj)
                    }
                }
            }

            if (started) {
                path.close()
                // Shaded interior Gold
                drawPath(path, color = Color(0x33FFDD00))
                drawPath(
                    path = path,
                    color = Color(0xFFFFD700),
                    style = Stroke(width = pc.getStrokeWidth(item.position, 4f))
                )
            }
        } else {
            // Lightning Boost Bolt Shape
            val localPoints = listOf(
                Vector3D(0f, 0.7f, 0f),
                Vector3D(-0.3f, 0.4f, 0f),
                Vector3D(0.1f, 0.4f, 0f),
                Vector3D(-0.1f, 0.1f, 0f),
                Vector3D(0.4f, 0.45f, 0f),
                Vector3D(0.0f, 0.45f, 0f)
            )

            val spinRad = (item.rotation * 1.5f) * Math.PI / 180f
            val projected = localPoints.map { pt ->
                val rx = pt.x * cos(spinRad)
                val rz = pt.x * sin(spinRad)
                pc.project(item.position + Vector3D(rx.toFloat(), pt.y, rz.toFloat()))
            }

            for (idx in 0 until projected.size - 1) {
                val pStart = projected[idx]
                val pEnd = projected[idx + 1]
                if (pStart != null && pEnd != null) {
                    drawLine(
                        color = Color(0xFF00FFCC),
                        start = pStart,
                        end = pEnd,
                        strokeWidth = pc.getStrokeWidth(item.position, 5f)
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------
// GEOMETRIC OBSTACLE DRAWING
// ------------------------------------------------------------------------
private fun DrawScope.drawObstacles(pc: ProjectionContext, config: LevelConfig) {
    config.obstacles.filter { it.active }.forEach { obs ->
        val sw = pc.getStrokeWidth(obs.position, 4f)
        
        when (obs.type) {
            ObstacleType.CONE -> {
                // Orange-White warning cone
                val bottomR = obs.size
                val baseVerts = listOf(
                    obs.position + Vector3D(-bottomR, 0f, -bottomR),
                    obs.position + Vector3D(bottomR, 0f, -bottomR),
                    obs.position + Vector3D(bottomR, 0f, bottomR),
                    obs.position + Vector3D(-bottomR, 0f, bottomR)
                )
                val tip = obs.position + Vector3D(0f, 1.1f, 0f)

                val projBase = baseVerts.map { pc.project(it) }
                val projTip = pc.project(tip)

                // Bottom polygon base
                val path = Path()
                var initB = false
                projBase.forEach { m ->
                    if (m != null) {
                        if (!initB) {
                            path.moveTo(m.x, m.y)
                            initB = true
                        } else {
                            pathLineTo(path, m)
                        }
                    }
                }
                if (initB) {
                    path.close()
                    drawPath(path, color = Color(0x33FF6D00))
                    drawPath(path, color = Color(0xFFFF6D00), style = Stroke(width = sw))
                }

                // Rib connections to Tip
                if (projTip != null) {
                    projBase.forEach { m ->
                        if (m != null) {
                            drawLine(Color(0xFFFF9100), m, projTip, strokeWidth = sw)
                        }
                    }
                }
            }
            ObstacleType.BOX -> {
                // 3D Wooden crate box line work
                val s = obs.size / 2f
                val boxVerts = listOf(
                    Vector3D(-s, 0f, -s), Vector3D(s, 0f, -s), Vector3D(s, 0f, s), Vector3D(-s, 0f, s),
                    Vector3D(-s, obs.size, -s), Vector3D(s, obs.size, -s), Vector3D(s, obs.size, s), Vector3D(-s, obs.size, s)
                ).map { obs.position + it }

                val proj = boxVerts.map { pc.project(it) }

                // Connect bounds draw skeleton lines
                fun l(i: Int, j: Int) {
                    val p1 = proj[i]
                    val p2 = proj[j]
                    if (p1 != null && p2 != null) {
                        drawLine(Color(0xFF8D6E63), p1, p2, strokeWidth = sw)
                    }
                }
                // Bottom
                l(0, 1); l(1, 2); l(2, 3); l(3, 0)
                // Top
                l(4, 5); l(5, 6); l(6, 7); l(7, 4)
                // Vertical pillars
                l(0, 4); l(1, 5); l(2, 6); l(3, 7)
                // X Cross side bars for premium graphics
                l(0, 5); l(1, 6); l(2, 7); l(3, 4)
            }
            ObstacleType.BARRIER -> {
                // Hazard orange barricade gate
                val w = obs.size
                val leftPost = obs.position + Vector3D(-w, 0f, 0f)
                val leftPostTop = obs.position + Vector3D(-w, 1.2f, 0f)
                val rightPost = obs.position + Vector3D(w, 0f, 0f)
                val rightPostTop = obs.position + Vector3D(w, 1.2f, 0f)

                val plB = pc.project(leftPost)
                val plT = pc.project(leftPostTop)
                val prB = pc.project(rightPost)
                val prT = pc.project(rightPostTop)

                if (plB != null && plT != null) {
                    drawLine(Color(0xFFFFD54F), plB, plT, strokeWidth = sw * 2f)
                }
                if (prB != null && prT != null) {
                    drawLine(Color(0xFFFFD54F), prB, prT, strokeWidth = sw * 2f)
                }
                if (plT != null && prT != null) {
                    // Thick Hazard Board bar
                    drawLine(Color(0xFFFFD54F), plT, prT, strokeWidth = sw * 4f)
                    // White striped hatch ticks
                    drawLine(Color(0xFF212121), plT, prT, strokeWidth = sw * 1.5f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 20f), 0f))
                }
            }
            ObstacleType.MOVING_DRUM -> {
                // Moving glowing warning drums
                val r = obs.size
                val hPos = 1.3f
                val baseProj = pc.project(obs.position)
                val topProj = pc.project(obs.position + Vector3D(0f, hPos, 0f))
                
                if (baseProj != null && topProj != null) {
                    val radiusScaled = (r * pc.w / (obs.position.z - pc.camZ + 1f)).coerceIn(10f, 200f)
                    // Inner glowing radioactive fluid cylinder
                    drawRect(
                        color = Color(0x3300E5FF),
                        topLeft = Offset(baseProj.x - radiusScaled, topProj.y),
                        size = Size(radiusScaled * 2, baseProj.y - topProj.y)
                    )
                    // Neon cyan outlines
                    drawLine(Color(0xFF00FFCC), Offset(baseProj.x - radiusScaled, baseProj.y), Offset(baseProj.x - radiusScaled, topProj.y), strokeWidth = sw)
                    drawLine(Color(0xFF00FFCC), Offset(baseProj.x + radiusScaled, baseProj.y), Offset(baseProj.x + radiusScaled, topProj.y), strokeWidth = sw)
                    drawLine(Color(0xFF00FFCC), Offset(baseProj.x - radiusScaled, topProj.y), Offset(baseProj.x + radiusScaled, topProj.y), strokeWidth = sw)
                    drawLine(Color(0xFF00FFCC), Offset(baseProj.x - radiusScaled, baseProj.y), Offset(baseProj.x + radiusScaled, baseProj.y), strokeWidth = sw)
                }
            }
            else -> {}
        }
    }
}

// ------------------------------------------------------------------------
// 3D PERSPECTIVE CYBERPUNK SKELETON SPORTS CAR
// ------------------------------------------------------------------------
private fun DrawScope.drawCarProjected(pc: ProjectionContext, car: CarState, wheelRot: Float) {
    // 1. CAR SKELETON WIREFRAME CONFIG
    val carLocalVertices = listOf(
        // Bottom perimeter chassis
        Vector3D(-0.55f, 0.12f, 1.1f),   // 0: FLB
        Vector3D(0.55f, 0.12f, 1.1f),    // 1: FRB
        Vector3D(0.55f, 0.15f, -1.2f),   // 2: RRB
        Vector3D(-0.55f, 0.15f, -1.2f),  // 3: RLB
        // Front angled bumper / windshield line
        Vector3D(-0.5f, 0.38f, 0.9f),    // 4: FL Hood
        Vector3D(0.5f, 0.38f, 0.9f),     // 5: FR Hood
        Vector3D(-0.48f, 0.45f, 0.45f),  // 6: Windshield Bottom L
        Vector3D(0.48f, 0.45f, 0.45f),   // 7: Windshield Bottom R
        // Sleek Capsule roof panel
        Vector3D(-0.35f, 0.72f, -0.1f),  // 8: Cabin Mid Top-L
        Vector3D(0.35f, 0.72f, -0.1f),   // 9: Cabin Mid Top-R
        Vector3D(-0.35f, 0.72f, -0.65f), // 10: Cabin Rear Top-L
        Vector3D(0.35f, 0.72f, -0.65f),  // 11: Cabin Rear Top-R
        // Cyber Spoilers
        Vector3D(-0.6f, 0.88f, -1.22f),  // 12: Spoiler Tip L
        Vector3D(0.6f, 0.88f, -1.22f)    // 13: Spoiler Tip R
    )

    // Compute relative to world coordinates
    val cosY = cos(car.yaw)
    val sinY = sin(car.yaw)

    fun localToWorld(pt: Vector3D): Vector3D {
        val wx = car.position.x + pt.x * cosY + pt.z * sinY
        val wz = car.position.z - pt.x * sinY + pt.z * cosY
        val wy = car.position.y + pt.y
        return Vector3D(wx, wy, wz)
    }

    val worldVertices = carLocalVertices.map { localToWorld(it) }
    val projected = worldVertices.map { pc.project(it) }

    // Wireframe bone connectors helper
    val carStroke = pc.getStrokeWidth(car.position, 4.5f)
    fun link(o1: Int, o2: Int, col: Color) {
        val p1 = projected[o1]
        val p2 = projected[o2]
        if (p1 != null && p2 != null) {
            drawLine(col, p1, p2, strokeWidth = carStroke)
        }
    }

    // Draw Sleek Cyan outline panels
    val shellColor = Color(0xEE00FFD5)
    val coreGreen = Color(0xFF00FFCC)

    // Floor Base Frame
    link(0, 1, shellColor); link(1, 2, shellColor); link(2, 3, shellColor); link(3, 0, shellColor)
    // Low Hood Profile
    link(0, 4, shellColor); link(1, 5, shellColor); link(4, 5, coreGreen)
    // Dashboard Nose
    link(4, 6, shellColor); link(5, 7, shellColor); link(6, 7, coreGreen)
    // Windshield Pillars
    link(6, 8, shellColor); link(7, 9, shellColor); link(8, 9, coreGreen)
    // Glass Cage Roof
    link(8, 10, shellColor); link(9, 11, shellColor); link(10, 11, coreGreen)
    // Angled Rear deck
    link(10, 2, shellColor); link(11, 3, shellColor)
    // Spoilers connections
    link(10, 12, Color(0xFFFF00A0)); link(11, 13, Color(0xFFFF00A0)); link(12, 13, Color(0xFFFF00A0))

    // 2. BACK NEON EXHAUST THRUSTER (Jet Exhaust Flame)
    val exhaustCenterLocal = Vector3D(0f, 0.28f, -1.25f)
    val exhaustWorld = localToWorld(exhaustCenterLocal)
    val exhaustProj = pc.project(exhaustWorld)
    if (exhaustProj != null) {
        // Red Hot Rocket glow ellipse depending on motion acceleration speed
        val exhaustScale = (4f + car.speed * 1.8f) / ((car.position.z - pc.camZ) * 0.08f + 1f).coerceAtLeast(1f)
        drawCircle(
            color = Color(0xFFFF1744),
            radius = exhaustScale * 2.5f,
            center = exhaustProj
        )
        drawCircle(
            color = Color(0xFFFFD54F),
            radius = exhaustScale * 1.2f,
            center = exhaustProj
        )
    }

    // 3. STEERABLE ACTIVE 3D WHEELS (Pivoted Front Wheels)
    val wheelOffsets = listOf(
        Vector3D(-0.62f, 0.12f, 0.72f),  // Front Left Center
        Vector3D(0.62f, 0.12f, 0.72f),   // Front Right Center
        Vector3D(-0.62f, 0.15f, -0.72f), // Rear Left Center
        Vector3D(0.62f, 0.15f, -0.72f)   // Rear Right Center
    )

    wheelOffsets.forEachIndexed { iW, center ->
        // Compute wheel rotated vertices
        // Front wheels take steer angle deflection
        val steerRotation = if (iW < 2) car.steerAngle else 0f
        
        // Let's model wheel rim points (circular hexagon lying vertically)
        val rRadius = 0.22f
        val wSegments = 6
        val path = Path()
        var initW = false

        for (u in 0..wSegments) {
            val aRad = (u * 2 * Math.PI / wSegments).toFloat()
            // Circle on Local Y/Z plane
            val basePt = Vector3D(0f, rRadius * sin(aRad), rRadius * cos(aRad))
            
            // Apply Steer rotation around Y local Axis
            val cosS = cos(steerRotation)
            val sinS = sin(steerRotation)
            val localX = basePt.x * cosS + basePt.z * sinS
            val localZ = basePt.z * cosS - basePt.x * sinS
            val localY = basePt.y

            val pivotedPt = center + Vector3D(localX, localY, localZ)
            val worldPt = localToWorld(pivotedPt)
            val projPt = pc.project(worldPt)
            
            if (projPt != null) {
                if (!initW) {
                    path.moveTo(projPt.x, projPt.y)
                    initW = true
                } else {
                    pathLineTo(path, projPt)
                }
            }
        }

        if (initW) {
            path.close()
            drawPath(path, color = Color(0xFF1E2436))
            drawPath(
                path = path,
                color = Color(0xFFFF00D4), // Magenta Tires
                style = Stroke(width = pc.getStrokeWidth(car.position, 3.5f))
            )
        }
    }
}

// Spark Particles
private fun DrawScope.drawParticles(pc: ProjectionContext, list: List<Particle>) {
    list.forEach { p ->
        val proj = pc.project(p.position)
        if (proj != null) {
            val rad = pc.getStrokeWidth(p.position, 5.5f) * (p.life / p.maxLife)
            drawCircle(
                color = Color(p.color).copy(alpha = (p.life / p.maxLife).coerceIn(0f, 1f)),
                radius = rad.coerceAtLeast(1f),
                center = proj
            )
        }
    }
}

// Path compatibility wrappers
private fun pathLineTo(path: Path, offset: Offset) {
    path.lineTo(offset.x, offset.y)
}

private fun dpToRad(dpVal: Int): Double {
    return Math.toRadians(dpVal.toDouble())
}

// ------------------------------------------------------------------------
// UI OVERLAYS PARTS
// ------------------------------------------------------------------------

@Composable
fun WelcomeOverlayScreen(
    currentLevel: Int,
    highScore: Int,
    recordsList: List<GameRecord>,
    onSelectLevel: (Int) -> Unit,
    onStartGame: () -> Unit,
    onClearRecords: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .shadow(16.dp, RoundedCornerShape(24.dp))
                .background(Color(0xE60A0D14), RoundedCornerShape(24.dp))
                .border(1.5.dp, Color(0xFF00FFD5), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Neon Head Logo
            Text(
                text = "NEON DRIVE AR",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00FFCC),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "SURFACE TRACK RACING MECHANICS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0x9900FFFF),
                modifier = Modifier.padding(bottom = 20.dp)
            )

            // Select Levels Cards
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(1, 2, 3).forEach { lvl ->
                    val selected = currentLevel == lvl
                    val cardBg = if (selected) Color(0xFF142435) else Color(0x6610141D)
                    val cardBorder = if (selected) Color(0xFF00FFCC) else Color(0x3300FFCC)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardBg)
                            .border(1.5.dp, cardBorder, RoundedCornerShape(12.dp))
                            .clickable { onSelectLevel(lvl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "LVL $lvl",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF00FFCC) else Color.White
                            )
                            Text(
                                when (lvl) {
                                    1 -> "AERO"
                                    2 -> "CYBER"
                                    else -> "OMEGA"
                                },
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }

            // Description of active level
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0x3300FFD5)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Current Name: " + when (currentLevel) {
                            1 -> "Aero Neon Grid"
                            2 -> "Cyber Dodge Maze"
                            else -> "Omega Grand Circuit"
                        },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = when (currentLevel) {
                            1 -> "Perfect learning ground. Checkpoints spaced 40m apart. Light cones."
                            2 -> "Features rapid sweeping robotic drums and deep pothole traps."
                            else -> "Precision driver speedrun. Extreme collision obstacles, narrow floor grid."
                        },
                        fontSize = 11.sp,
                        color = Color(0xCC00FFD5)
                    )
                }
            }

            // Display Historic Level Records
            Text(
                "HISTORIC ATTEMPTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 6.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0x330C0E14))
                    .border(1.dp, Color(0x3300FFD5), RoundedCornerShape(8.dp))
            ) {
                if (recordsList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No completed scores yet.", color = Color.Gray, fontSize = 11.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(recordsList.filter { it.level == currentLevel }) { rec ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0x22FFFFFF), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Score: ${rec.score}", fontSize = 11.sp, color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                                Text("Coins: ${rec.coinsCollected}", fontSize = 11.sp, color = Color(0xFFFFD700))
                                Text("Time: ${rec.timeUsed}s", fontSize = 11.sp, color = Color.LightGray)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // BIG INITIAL START BUTTON
            Button(
                onClick = onStartGame,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("submit_button")
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INITIALIZE AR CORES",
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            if (recordsList.isNotEmpty()) {
                Text(
                    "Clear Stats History",
                    color = Color.Red,
                    fontSize = 11.sp,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { onClearRecords() }
                )
            }
        }
    }
}

@Composable
fun ScanningOverlayScreen(
    progress: Float,
    onStartGame: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xE60D0F16))
                .border(1.dp, Color(0xFF00FFD5), RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "CAMERA SCANNING MODE",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00FFCC),
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Point your mobile camera towards the floor grid surface.",
                fontSize = 11.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ProgressBar indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape),
                color = Color(0xFFFF00D4),
                trackColor = Color(0xFF1E2436)
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Surface Detection: ${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (progress >= 1.0f) Color(0xFF00FFCC) else Color.White
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("CANCEL")
                }
                
                Button(
                    onClick = onStartGame,
                    enabled = progress >= 1.0f,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC), disabledContainerColor = Color(0x3300FFCC)),
                    modifier = Modifier.weight(1.5f)
                ) {
                    Text("START DRIVE", color = if (progress >= 1.0f) Color.Black else Color.Gray)
                }
            }
        }
    }
}

// Interactive landscape / portrait thumb control overlay
@Composable
fun PlayingHUDOverlay(
    score: Int,
    coins: Int,
    timer: Int,
    car: CarState,
    levelConfig: LevelConfig,
    levelNum: Int,
    onPause: () -> Unit,
    onPressLeft: (Boolean) -> Unit,
    onPressRight: (Boolean) -> Unit,
    onPressAccel: (Boolean) -> Unit,
    onPressBrake: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        
        // top status HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 24.dp, end = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed indicator and level status
            Column {
                Text(
                    "LVL $levelNum: ${levelConfig.name.uppercase()}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "${(car.speed * 8).toInt()} KM/H",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF00FFCC)
                    )
                }
            }

            // Health bar & Timer
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shield health
                Column(horizontalAlignment = Alignment.End) {
                    Text("CYBER CORE HULL", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .width(100.dp)
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Color(0x331E2436))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(car.health / 100f)
                                .background(
                                    if (car.health > 50f) Color(0xFF00FFCC) else if (car.health > 25f) Color(
                                        0xFFFFD54F
                                    ) else Color(0xFFFF1744)
                                )
                        )
                    }
                }

                // Score + Coin details
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("SCORE", fontSize = 9.sp, color = Color.LightGray)
                    Text("$score", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF00D4))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$coins", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Time ticking indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = if (timer < 10) Color.Red else Color.White, modifier = Modifier.size(18.dp))
                    Text("${timer}s", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = if (timer < 10) Color.Red else Color.White)
                }

                // PAUSE TRIGGER BUTTON
                IconButton(
                    onClick = onPause,
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0x66000000), CircleShape)
                        .border(1.dp, Color(0xFF00FFD5), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Pause", tint = Color.White)
                }
            }
        }

        // Checkpoint target meters
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
                .background(Color(0x77000000), RoundedCornerShape(8.dp))
                .border(0.5.dp, Color(0xFF00FFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            val progressZ = (car.position.z / levelConfig.targetZ).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("DIST TO GATE", fontSize = 8.sp, color = Color.LightGray)
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(Color(0x44FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progressZ)
                            .background(Color(0xFF00FFCC))
                    )
                }
                Text("${(levelConfig.targetZ - car.position.z).coerceAtLeast(0f).toInt()}m", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        // Thumb game controller pads
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.38f)
                .align(Alignment.BottomCenter)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
        ) {
            // LEFT THUMB CONTROL: STEERING WHEEL PADS
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .width(180.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT STEERING Arrow Button
                GamePadButton(
                    modifier = Modifier.size(68.dp),
                    iconStr = "◀",
                    onPress = onPressLeft
                )

                // RIGHT STEERING Arrow Button
                GamePadButton(
                    modifier = Modifier.size(68.dp),
                    iconStr = "▶",
                    onPress = onPressRight
                )
            }

            // RIGHT THUMB CONTROL: SPEED ACCELERATOR / BRAKE
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .width(180.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // BRAKE PEDAL BUTTON
                GamePadButton(
                    modifier = Modifier.size(68.dp),
                    iconStr = "BRAKE",
                    bgColor = Color(0x66FF1744),
                    borderColor = Color(0xFFFF1744),
                    onPress = onPressBrake
                )

                // GAS PEDAL BUTTON
                GamePadButton(
                    modifier = Modifier.size(68.dp),
                    iconStr = "GAS",
                    bgColor = Color(0x6600FFD5),
                    borderColor = Color(0xFF00FFCC),
                    onPress = onPressAccel
                )
            }
        }
    }
}

@Composable
fun GamePadButton(
    modifier: Modifier = Modifier,
    iconStr: String,
    bgColor: Color = Color(0x661A2234),
    borderColor: Color = Color(0xFF00E5FF),
    onPress: (Boolean) -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(bgColor)
            .border(2.dp, borderColor, CircleShape)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val type = event.changes.firstOrNull() ?: continue
                        if (type.pressed) {
                            onPress(true)
                        } else if (type.previousPressed) {
                            onPress(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = iconStr,
            fontSize = if (iconStr.length > 2) 11.sp else 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
fun PauseMenuOverlay(
    levelName: String,
    score: Int,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x990A0D14)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFF0A0D14), RoundedCornerShape(16.dp))
                .border(1.5.dp, Color(0xFFFF00D4), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "GAME PAUSED",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFF00D4),
                fontFamily = FontFamily.Monospace
            )
            Text(levelName.uppercase(), fontSize = 12.sp, color = Color.Gray)
            Text("Current Score: $score", fontSize = 14.sp, color = Color.White)

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
            ) {
                Text("RESUME DRIVE", color = Color.Black, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("RESTART MISSION", color = Color.White)
            }

            OutlinedButton(
                onClick = onMainMenu,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK TO HANGAR", color = Color.LightGray)
            }
        }
    }
}

@Composable
fun MissionCompleteOverlay(
    levelName: String,
    finalScore: Int,
    bonus: Int,
    coins: Int,
    timerUsed: Int,
    onNextLevel: () -> Unit,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC05060A)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .shadow(16.dp, RoundedCornerShape(20.dp))
                .background(Color(0xFF0A0D14), RoundedCornerShape(20.dp))
                .border(2.dp, Color(0xFF00FFCC), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Glowing complete title
            Text(
                text = "MISSION ACCOMPLISHED",
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF00FFCC),
                textAlign = TextAlign.Center
            )
            Text(levelName, fontSize = 13.sp, color = Color.Gray)

            // Neon Stars Award
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val stars = if (finalScore > 2000) 3 else if (finalScore > 1000) 2 else 1
                for (i in 1..3) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = if (i <= stars) Color(0xFFFFD700) else Color(0x33FFFFFF),
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Score details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x2200FFD5), RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Time Used:", fontSize = 12.sp, color = Color.LightGray)
                    Text("${timerUsed}s", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Time Bonus Added:", fontSize = 12.sp, color = Color.LightGray)
                    Text("+$bonus", fontSize = 12.sp, color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Coins Extracted:", fontSize = 12.sp, color = Color.LightGray)
                    Text("$coins", fontSize = 12.sp, color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(color = Color(0x3300FFD5))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("TOTAL SCORE:", fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    Text("$finalScore", fontSize = 16.sp, color = Color(0xFFFF00D4), fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onNextLevel,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC))
            ) {
                Text("CORES PROCEED SYSTEM", color = Color.Black, fontWeight = FontWeight.ExtraBold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onRestart,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("RETRY", color = Color.White)
                }
                OutlinedButton(
                    onClick = onMainMenu,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("HANGAR", color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun GameOverOverlay(
    score: Int,
    healthRemaining: Float,
    timerRemaining: Int,
    onRestart: () -> Unit,
    onMainMenu: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC1A090D)), // Crimson dim backing
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .shadow(16.dp, RoundedCornerShape(16.dp))
                .background(Color(0xFF0F0B0C), RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFFFF1744), RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFF1744),
                modifier = Modifier.size(48.dp)
            )

            Text(
                "SYSTEM ANOMALY: CRASHED",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFFF1744)
            )

            Text(
                text = if (healthRemaining <= 0) "Cyber sports car core hull integrity depleted! Critical impact occurred." else "Time limit expired. Mission terminal timed out.",
                fontSize = 11.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("CRASH STAGE SCORE: $score", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF1744))
            ) {
                Text("RESTART DRIVE ENGINE", color = Color.White, fontWeight = FontWeight.Bold)
            }

            OutlinedButton(
                onClick = onMainMenu,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("EXIT TO HANGAR", color = Color.LightGray)
            }
        }
    }
}
