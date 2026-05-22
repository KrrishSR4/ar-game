package com.example.ui

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRecord
import com.example.data.GameRepository
import com.example.domain.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class GamePhase {
    WELCOME,
    AR_SCANNING,
    PLAYING,
    PAUSED,
    MISSION_COMPLETE,
    GAME_OVER
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    val historyList: StateFlow<List<GameRecord>>
    
    init {
        val db = GameDatabase.getDatabase(application)
        repository = GameRepository(db.gameRecordDao())
        historyList = repository.allRecords.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    // Audio & Haptics
    private val audioHapticSystem = AudioHapticSystem(application)

    // Game Core State
    private val _gamePhase = MutableStateFlow(GamePhase.WELCOME)
    val gamePhase: StateFlow<GamePhase> = _gamePhase.asStateFlow()

    private val _currentLevel = MutableStateFlow(1)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _coinsCollected = MutableStateFlow(0)
    val coinsCollected: StateFlow<Int> = _coinsCollected.asStateFlow()

    private val _timerSeconds = MutableStateFlow(0)
    val timerSeconds: StateFlow<Int> = _timerSeconds.asStateFlow()

    private val _carState = MutableStateFlow(CarState())
    val carState: StateFlow<CarState> = _carState.asStateFlow()

    private val _levelConfig = MutableStateFlow(PredefinedLevels.createLevel(1))
    val levelConfig: StateFlow<LevelConfig> = _levelConfig.asStateFlow()

    private val _highScore = MutableStateFlow(0)
    val highScore: StateFlow<Int> = _highScore.asStateFlow()

    // Screen Shake effect magnitude
    private val _screenShake = MutableStateFlow(0f)
    val screenShake: StateFlow<Float> = _screenShake.asStateFlow()

    // Simulated floor scanning percentage
    private val _scanningProgress = MutableStateFlow(0f)
    val scanningProgress: StateFlow<Float> = _scanningProgress.asStateFlow()

    // 3D Particles in the environment
    val activeParticles = mutableStateListOf<Particle>()

    // Inputs
    var accelPressed = false
    var brakePressed = false
    var steerLeft = false
    var steerRight = false

    // Game Loop Job
    private var gameLoopJob: Job? = null
    private var timerLoopJob: Job? = null

    fun selectLevel(lvl: Int) {
        _currentLevel.value = lvl
        _levelConfig.value = PredefinedLevels.createLevel(lvl)
        
        // Fetch historical high score for this level
        viewModelScope.launch {
            repository.getHighScoreForLevel(lvl).collect { hs ->
                _highScore.value = hs ?: 0
            }
        }
    }

    fun startScanning() {
        _gamePhase.value = GamePhase.AR_SCANNING
        _scanningProgress.value = 0f
        
        // Simulate Camera surface plane scanning
        viewModelScope.launch {
            var progress = 0f
            while (progress < 1.0f) {
                delay(40)
                progress += 0.02f
                _scanningProgress.value = progress.coerceAtMost(1.0f)
            }
        }
    }

    fun confirmSurfaceAndStartGame() {
        if (_scanningProgress.value >= 1.0f) {
            setupGame()
            _gamePhase.value = GamePhase.PLAYING
            startGameLoops()
        }
    }

    fun resumeGame() {
        _gamePhase.value = GamePhase.PLAYING
        startGameLoops()
    }

    fun pauseGame() {
        _gamePhase.value = GamePhase.PAUSED
        stopGameLoops()
    }

    fun restartGame() {
        setupGame()
        _gamePhase.value = GamePhase.PLAYING
        startGameLoops()
    }

    fun backToMainMenu() {
        _gamePhase.value = GamePhase.WELCOME
        stopGameLoops()
    }

    fun clearAllRecords() {
        viewModelScope.launch {
            repository.clearHistory()
        }
    }

    private fun setupGame() {
        _score.value = 0
        _coinsCollected.value = 0
        
        val config = PredefinedLevels.createLevel(_currentLevel.value)
        _levelConfig.value = config
        _timerSeconds.value = config.totalTime
        
        activeParticles.clear()
        
        // Reset Car
        val newCarState = CarState()
        newCarState.position = Vector3D(0f, 0f, 0f)
        newCarState.yaw = 0f
        newCarState.speed = 0f
        newCarState.steerAngle = 0f
        newCarState.health = 100f
        
        _carState.value = newCarState
        _screenShake.value = 0f
    }

    private fun startGameLoops() {
        stopGameLoops() // Prevent duplicates
        
        // Physics & collision tick at 60fps
        var lastTime = System.nanoTime()
        gameLoopJob = viewModelScope.launch {
            while (_gamePhase.value == GamePhase.PLAYING) {
                val currentTime = System.nanoTime()
                val dt = ((currentTime - lastTime) / 1_000_000_000.0).toFloat().coerceIn(0.005f, 0.05f)
                lastTime = currentTime
                
                updateGameTick(dt)
                delay(12) // Aim ~70fps mechanics
            }
        }

        // Gameplay timers (1s increments)
        timerLoopJob = viewModelScope.launch {
            while (_gamePhase.value == GamePhase.PLAYING) {
                delay(1000)
                if (_timerSeconds.value > 0) {
                    _timerSeconds.value -= 1
                    if (_timerSeconds.value == 0) {
                        triggerGameOver()
                    }
                }
            }
        }
    }

    private fun stopGameLoops() {
        gameLoopJob?.cancel()
        gameLoopJob = null
        timerLoopJob?.cancel()
        timerLoopJob = null
    }

    private fun updateGameTick(dt: Float) {
        val car = _carState.value
        val config = _levelConfig.value

        // 1. Update car physics
        car.update(dt, accelPressed, brakePressed, steerLeft, steerRight)

        // 2. Update moving obstacles
        config.obstacles.forEach { obs ->
            if (obs.isMoving) {
                val nextX = obs.position.x + obs.moveDir * obs.moveSpeed * dt
                val limitLeft = obs.startX - obs.moveRange / 2f
                val limitRight = obs.startX + obs.moveRange / 2f
                
                if (nextX < limitLeft) {
                    obs.position = Vector3D(limitLeft, obs.position.y, obs.position.z)
                    obs.moveDir = 1f
                } else if (nextX > limitRight) {
                    obs.position = Vector3D(limitRight, obs.position.y, obs.position.z)
                    obs.moveDir = -1f
                } else {
                    obs.position = Vector3D(nextX, obs.position.y, obs.position.z)
                }
            }
        }

        // 3. Collision with obstacles
        val carSize = 1.1f
        config.obstacles.forEach { obs ->
            if (obs.active) {
                val dist = car.position.distanceTo(obs.position)
                val hitDist = (carSize + obs.size) * 0.85f
                
                if (dist < hitDist) {
                    // Handle Collision impact
                    obs.active = false
                    car.bounceBack()
                    
                    // Shake camera
                    _screenShake.value = 0.5f
                    
                    val dmg = when (obs.type) {
                        ObstacleType.CONE -> 10f
                        ObstacleType.BOX -> 20f
                        ObstacleType.BARRIER -> 30f
                        ObstacleType.POTHOLE -> 25f
                        ObstacleType.MOVING_DRUM -> 35f
                    }
                    car.health = (car.health - dmg).coerceAtLeast(0f)
                    _score.value = (_score.value - 100).coerceAtLeast(0)
                    
                    // Feedback API
                    audioHapticSystem.vibrateCrash()
                    audioHapticSystem.playCrashSound()
                    
                    // Generate spark particles in 3D
                    spawnSparks(obs.position, obs.type)
                    
                    if (car.health <= 0f) {
                        triggerGameOver()
                    }
                }
            }
        }

        // 4. Collect coins and power-ups
        config.collectibles.forEach { coll ->
            if (coll.active) {
                // Update rotation angle
                coll.rotation = (coll.rotation + 180f * dt) % 360f
                
                val dist = car.position.distanceTo(coll.position)
                val collectDist = carSize + coll.size
                
                if (dist < collectDist) {
                    coll.active = false
                    audioHapticSystem.vibrateCollect()
                    
                    if (coll.type == CollectibleType.COIN) {
                        _coinsCollected.value += 1
                        _score.value += 150
                        audioHapticSystem.playCollectSound()
                        spawnCollectBurst(coll.position, 0xFF00FFCC)
                    } else if (coll.type == CollectibleType.BOOST) {
                        // Activate Speed Boost
                        car.speed = (car.speed + 7.5f).coerceAtMost(car.maxSpeed * 1.35f)
                        _score.value += 200
                        audioHapticSystem.playBoostSound()
                        spawnCollectBurst(coll.position, 0xFFFFCC00)
                    }
                }
            }
        }

        // 5. Reach checkpoints and destination
        config.checkpoints.forEach { cp ->
            if (cp.active) {
                val dist = car.position.distanceTo(cp.position)
                if (dist < cp.radius + carSize) {
                    cp.active = false
                    audioHapticSystem.playCheckpointSound()
                    _score.value += 400
                    
                    // Checkpoint reward time added!
                    _timerSeconds.value += 12
                    
                    spawnCollectBurst(cp.position, 0xFF00FFFF)
                }
            }
        }

        // Check level completion
        if (car.position.z >= config.targetZ) {
            triggerLevelComplete()
        }

        // 6. Update particles
        val iterator = activeParticles.listIterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
            } else {
                p.position = p.position + p.velocity * dt
            }
        }

        // Fade out screen vibration shake
        if (_screenShake.value > 0f) {
            _screenShake.value = (_screenShake.value - dt * 2f).coerceAtLeast(0f)
        }
    }

    private fun spawnSparks(origin: Vector3D, type: ObstacleType) {
        val color = when (type) {
            ObstacleType.CONE -> 0xFFFF6F00 // Orange
            ObstacleType.BOX -> 0xFF8D6E63 // Wood Brown
            ObstacleType.BARRIER -> 0xFFFFD54F // High-Vis Yellow
            ObstacleType.POTHOLE -> 0xFFE53935 // Crimson Red
            ObstacleType.MOVING_DRUM -> 0xFF00E5FF // Hot Cyan
        }
        
        // Spawn 25 sparks bouncing outward
        for (i in 0 until 25) {
            val vx = Random.nextFloat() * 10f - 5f
            val vy = Random.nextFloat() * 6f + 2f
            val vz = Random.nextFloat() * 10f - 5f
            
            activeParticles.add(
                Particle(
                    position = origin + Vector3D(0f, 0.2f, 0f),
                    velocity = Vector3D(vx, vy, vz),
                    color = color,
                    life = Random.nextFloat() * 0.8f + 0.4f,
                    maxLife = 1.2f
                )
            )
        }
    }

    private fun spawnCollectBurst(origin: Vector3D, color: Long) {
        for (i in 0 until 15) {
            val vx = Random.nextFloat() * 6f - 3f
            val vy = Random.nextFloat() * 4f + 1f
            val vz = Random.nextFloat() * 6f - 3f
            activeParticles.add(
                Particle(
                    position = origin,
                    velocity = Vector3D(vx, vy, vz),
                    color = color,
                    life = Random.nextFloat() * 0.6f + 0.3f,
                    maxLife = 0.9f
                )
            )
        }
    }

    private fun triggerLevelComplete() {
        _gamePhase.value = GamePhase.MISSION_COMPLETE
        stopGameLoops()
        
        // Calculate bonus score
        val timeBonus = _timerSeconds.value * 25
        val finalScore = _score.value + timeBonus
        
        // Save score asynchronously to database Room
        viewModelScope.launch {
            repository.insertRecord(
                GameRecord(
                    level = _currentLevel.value,
                    score = finalScore,
                    timeUsed = PredefinedLevels.createLevel(_currentLevel.value).totalTime - _timerSeconds.value,
                    coinsCollected = _coinsCollected.value
                )
            )
        }
    }

    private fun triggerGameOver() {
        _gamePhase.value = GamePhase.GAME_OVER
        stopGameLoops()
    }

    override fun onCleared() {
        super.onCleared()
        audioHapticSystem.release()
    }
}
