package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import com.example.R

enum class NexoSoundEvent {
    UI_CLICK,
    DOWNLOAD_START,
    DOWNLOAD_SUCCESS,
    DOWNLOAD_ERROR
}

interface NexoAudioController {
    val isBgmPlaying: Boolean
    val isBgmAssetPresent: Boolean

    fun playUiClick()
    fun playDownloadStart()
    fun playDownloadSuccess()
    fun playDownloadError()

    /**
     * Background music controller scoped strictly to the Account Login Screen (MainLogin).
     * Must NOT be started on Splash, update/server/region checks, package download, or final loading.
     */
    fun startLoginBackgroundMusic()
    fun stopBackgroundMusic()
    fun pauseBackgroundMusic()
    fun resumeBackgroundMusic()
    fun release()
}

/**
 * Manages short UI & download sound effects via SoundPool with strict non-overlapping playback,
 * and plays the looping background theme (res/raw/bgm_main_theme.mp3) exclusively on MainLogin.
 */
class AndroidNexoAudioManager(private val context: Context) : NexoAudioController {

    private var soundPool: SoundPool? = null
    private val soundIds = mutableMapOf<NexoSoundEvent, Int>()
    private val loadedSounds = mutableSetOf<Int>()
    private var activeStreamId: Int = 0
    private var lastEventTimeMs: Long = 0L
    private var lastEvent: NexoSoundEvent? = null

    private var bgmPlayer: MediaPlayer? = null
    private var bgmAllowedInCurrentStage: Boolean = false

    override var isBgmPlaying: Boolean = false
        private set

    override val isBgmAssetPresent: Boolean
        get() = R.raw.bgm_main_theme != 0

    init {
        try {
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val pool = SoundPool.Builder()
                .setMaxStreams(1) // Strictly prevent overlapping SFX playback
                .setAudioAttributes(attrs)
                .build()

            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status == 0) {
                    loadedSounds.add(sampleId)
                }
            }

            soundIds[NexoSoundEvent.UI_CLICK] = pool.load(context, R.raw.sfx_ui_click, 1)
            soundIds[NexoSoundEvent.DOWNLOAD_START] = pool.load(context, R.raw.sfx_download_start, 1)
            soundIds[NexoSoundEvent.DOWNLOAD_SUCCESS] = pool.load(context, R.raw.sfx_download_success, 1)
            soundIds[NexoSoundEvent.DOWNLOAD_ERROR] = pool.load(context, R.raw.sfx_download_error, 1)
            soundPool = pool
        } catch (_: Throwable) {
            // Safe fallback in headless/JVM test environments without audio HAL
            soundPool = null
        }
    }

    override fun playUiClick() {
        playSingleShot(NexoSoundEvent.UI_CLICK, volume = 0.65f, minIntervalMs = 60L)
    }

    override fun playDownloadStart() {
        playSingleShot(NexoSoundEvent.DOWNLOAD_START, volume = 0.85f, minIntervalMs = 200L)
    }

    override fun playDownloadSuccess() {
        playSingleShot(NexoSoundEvent.DOWNLOAD_SUCCESS, volume = 0.90f, minIntervalMs = 300L)
    }

    override fun playDownloadError() {
        playSingleShot(NexoSoundEvent.DOWNLOAD_ERROR, volume = 0.90f, minIntervalMs = 300L)
    }

    private fun playSingleShot(event: NexoSoundEvent, volume: Float, minIntervalMs: Long) {
        val now = System.currentTimeMillis()
        if (lastEvent == event && (now - lastEventTimeMs) < minIntervalMs) {
            return
        }
        lastEvent = event
        lastEventTimeMs = now

        val pool = soundPool ?: return
        val sampleId = soundIds[event] ?: return

        try {
            // Stop any currently active sound effect before starting a new one
            if (activeStreamId != 0) {
                pool.stop(activeStreamId)
                activeStreamId = 0
            }
            if (loadedSounds.contains(sampleId)) {
                activeStreamId = pool.play(sampleId, volume, volume, 1, 0, 1.0f)
            }
        } catch (_: Throwable) {
            // Ignore audio playback errors gracefully
        }
    }

    override fun startLoginBackgroundMusic() {
        bgmAllowedInCurrentStage = true
        if (bgmPlayer == null) {
            try {
                bgmPlayer = MediaPlayer.create(context, R.raw.bgm_main_theme)?.apply {
                    isLooping = true
                    setVolume(0.55f, 0.55f)
                }
            } catch (_: Throwable) {
                bgmPlayer = null
            }
        }

        val player = bgmPlayer ?: return
        if (!player.isPlaying) {
            try {
                player.start()
                isBgmPlaying = true
            } catch (_: Throwable) {
                isBgmPlaying = false
            }
        } else {
            isBgmPlaying = true
        }
    }

    override fun stopBackgroundMusic() {
        bgmAllowedInCurrentStage = false
        try {
            bgmPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
                player.seekTo(0)
            }
        } catch (_: Throwable) {
            // Ignore MediaPlayer state errors
        } finally {
            isBgmPlaying = false
        }
    }

    override fun pauseBackgroundMusic() {
        try {
            bgmPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
            }
        } catch (_: Throwable) {
            // Ignore
        } finally {
            isBgmPlaying = false
        }
    }

    override fun resumeBackgroundMusic() {
        if (!bgmAllowedInCurrentStage) return
        try {
            bgmPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    isBgmPlaying = true
                }
            }
        } catch (_: Throwable) {
            isBgmPlaying = false
        }
    }

    override fun release() {
        bgmAllowedInCurrentStage = false
        try {
            if (activeStreamId != 0) {
                soundPool?.stop(activeStreamId)
            }
            soundPool?.release()
            soundPool = null
            bgmPlayer?.release()
            bgmPlayer = null
        } catch (_: Throwable) {
            // Ignore cleanup errors
        } finally {
            isBgmPlaying = false
        }
    }
}
