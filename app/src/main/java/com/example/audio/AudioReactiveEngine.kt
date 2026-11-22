package com.example.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import com.example.model.AudioFrame
import com.example.model.AudioTrackItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Real-time microphone capture & 24-band Goertzel spectrum analyzer + adaptive beat detector.
 */
class MicrophoneAudioEngine(private val context: Context) {

    private val _isCapturing = MutableStateFlow(false)
    val isCapturing: StateFlow<Boolean> = _isCapturing.asStateFlow()

    private val _audioFrame = MutableStateFlow(AudioFrame())
    val audioFrame: StateFlow<AudioFrame> = _audioFrame.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var captureJob: Job? = null

    fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun startCapture(scope: CoroutineScope) {
        if (!hasRecordPermission()) {
            _error.value = "Необходимо разрешение на доступ к микрофону"
            return
        }
        if (_isCapturing.value) return
        _error.value = null

        val sampleRate = 22050
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)

        val recorder = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 2
            )
        }.getOrNull()

        if (recorder == null || recorder.state != AudioRecord.STATE_INITIALIZED) {
            _error.value = "Не удалось запустить микрофон устройства"
            runCatching { recorder?.release() }
            return
        }

        _isCapturing.value = true
        captureJob = scope.launch(Dispatchers.Default) {
            val frameSize = 512
            val pcm = ShortArray(frameSize)
            val bandFreqs = floatArrayOf(
                55f, 75f, 100f, 130f, 170f, 220f,
                290f, 380f, 500f, 650f, 840f, 1080f,
                1380f, 1750f, 2200f, 2750f, 3400f, 4150f,
                5000f, 5950f, 7000f, 8100f, 9200f, 10200f
            )
            val coeffs = FloatArray(bandFreqs.size) { i ->
                (2.0 * cos(2.0 * PI * bandFreqs[i] / sampleRate)).toFloat()
            }
            var rollingBass = 0.05f
            var lastBeatTime = 0L

            try {
                recorder.startRecording()
                while (isActive && _isCapturing.value) {
                    val read = recorder.read(pcm, 0, frameSize)
                    if (read <= 0) {
                        delay(10)
                        continue
                    }

                    // Normalize and compute RMS
                    var sumSq = 0.0
                    val samples = FloatArray(read)
                    for (i in 0 until read) {
                        val s = pcm[i] / 32768f
                        samples[i] = s
                        sumSq += s * s
                    }
                    val rms = sqrt(sumSq / read).toFloat()
                    val level = (rms * 4.5f).coerceIn(0f, 1f)

                    // Compute 24 frequency bars via Goertzel algorithm
                    val bars = MutableList(bandFreqs.size) { 0f }
                    for (b in bandFreqs.indices) {
                        val coeff = coeffs[b]
                        var sPrev = 0f
                        var sPrev2 = 0f
                        for (i in 0 until read) {
                            val s = samples[i] + coeff * sPrev - sPrev2
                            sPrev2 = sPrev
                            sPrev = s
                        }
                        val power = (sPrev * sPrev + sPrev2 * sPrev2 - coeff * sPrev * sPrev2).coerceAtLeast(0f)
                        val mag = sqrt(power) / (read * 0.25f)
                        bars[b] = (ln(1f + mag * 8f) / 2.2f).coerceIn(0f, 1f)
                    }

                    val bass = bars.subList(0, 6).average().toFloat().coerceIn(0f, 1f)
                    val mid = bars.subList(6, 15).average().toFloat().coerceIn(0f, 1f)
                    val treble = bars.subList(15, 24).average().toFloat().coerceIn(0f, 1f)

                    val now = System.currentTimeMillis()
                    val isBeat = bass > (rollingBass * 1.48f + 0.07f) && (now - lastBeatTime) > 190L
                    if (isBeat) lastBeatTime = now
                    rollingBass = rollingBass * 0.90f + bass * 0.10f

                    _audioFrame.value = AudioFrame(
                        level = level,
                        bass = bass,
                        mid = mid,
                        treble = treble,
                        isBeat = isBeat,
                        bars = bars
                    )
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Ошибка чтения аудио"
            } finally {
                runCatching { recorder.stop() }
                runCatching { recorder.release() }
                _isCapturing.value = false
                _audioFrame.value = AudioFrame()
            }
        }
    }

    fun stopCapture() {
        _isCapturing.value = false
        captureJob?.cancel()
        captureJob = null
        _audioFrame.value = AudioFrame()
    }
}

/**
 * Local Audio Player using zero-permission SAF URIs + Android MediaPlayer + Audio Visualizer FFT.
 */
class LocalMusicPlayerEngine(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null

    private val _playlist = MutableStateFlow<List<AudioTrackItem>>(emptyList())
    val playlist: StateFlow<List<AudioTrackItem>> = _playlist.asStateFlow()

    private val _currentIndex = MutableStateFlow(-1)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _audioFrame = MutableStateFlow(AudioFrame())
    val audioFrame: StateFlow<AudioFrame> = _audioFrame.asStateFlow()

    private var rollingBass = 0.05f
    private var lastBeatTime = 0L

    fun addTrackUris(uris: List<Uri>) {
        val current = _playlist.value.toMutableList()
        val existingSet = current.map { it.uriString }.toHashSet()
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val str = uri.toString()
            if (str !in existingSet) {
                val meta = extractMetadata(uri)
                current.add(meta)
                existingSet.add(str)
            }
        }
        _playlist.value = current
        if (_currentIndex.value < 0 && current.isNotEmpty()) {
            _currentIndex.value = 0
        }
    }

    fun removeTrack(index: Int) {
        val list = _playlist.value.toMutableList()
        if (index !in list.indices) return
        if (index == _currentIndex.value) {
            stopAndRelease()
        }
        list.removeAt(index)
        _playlist.value = list
        _currentIndex.value = when {
            list.isEmpty() -> -1
            _currentIndex.value >= list.size -> list.lastIndex
            else -> _currentIndex.value
        }
    }

    fun playTrackAt(index: Int) {
        val list = _playlist.value
        if (index !in list.indices) return
        val item = list[index]
        stopAndRelease()
        _currentIndex.value = index

        runCatching {
            val mp = MediaPlayer().apply {
                setDataSource(context, Uri.parse(item.uriString))
                prepare()
                setOnCompletionListener {
                    playNext()
                }
                start()
            }
            mediaPlayer = mp
            _durationMs.value = mp.duration.toLong().coerceAtLeast(1L)
            _positionMs.value = 0L
            _isPlaying.value = true
            attachVisualizer(mp.audioSessionId)
        }.onFailure {
            _isPlaying.value = false
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            val idx = _currentIndex.value.coerceAtLeast(0)
            if (idx in _playlist.value.indices) {
                playTrackAt(idx)
            }
            return
        }
        if (mp.isPlaying) {
            mp.pause()
            _isPlaying.value = false
            _audioFrame.value = AudioFrame()
        } else {
            mp.start()
            _isPlaying.value = true
        }
    }

    fun playNext() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val next = (_currentIndex.value + 1) % list.size
        playTrackAt(next)
    }

    fun playPrevious() {
        val list = _playlist.value
        if (list.isEmpty()) return
        val prev = if (_currentIndex.value <= 0) list.lastIndex else _currentIndex.value - 1
        playTrackAt(prev)
    }

    fun seekTo(fraction: Float) {
        val mp = mediaPlayer ?: return
        val target = (fraction.coerceIn(0f, 1f) * _durationMs.value).toInt()
        runCatching {
            mp.seekTo(target)
            _positionMs.value = target.toLong()
        }
    }

    fun pollPlaybackProgress() {
        val mp = mediaPlayer ?: return
        if (_isPlaying.value) {
            runCatching {
                _positionMs.value = mp.currentPosition.toLong()
                if (mp.duration > 0) _durationMs.value = mp.duration.toLong()
            }
        }
    }

    fun stopAndRelease() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        runCatching {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
        mediaPlayer = null
        _isPlaying.value = false
        _audioFrame.value = AudioFrame()
    }

    private fun attachVisualizer(sessionId: Int) {
        val hasAudioPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasAudioPerm) return

        runCatching {
            val vis = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) = Unit

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 4 || !_isPlaying.value) return
                            processFftBytes(fft)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
            visualizer = vis
        }
    }

    private fun processFftBytes(fft: ByteArray) {
        val binCount = fft.size / 2
        val barCount = 24
        val bars = MutableList(barCount) { 0f }
        val binsPerBar = (binCount / barCount).coerceAtLeast(1)

        for (bar in 0 until barCount) {
            var acc = 0f
            val startBin = (bar * binsPerBar).coerceAtLeast(1)
            val endBin = ((bar + 1) * binsPerBar).coerceAtMost(binCount - 1)
            for (k in startBin..endBin) {
                val real = fft[2 * k].toFloat()
                val imag = fft[2 * k + 1].toFloat()
                acc += hypot(real, imag)
            }
            val avg = acc / (endBin - startBin + 1).coerceAtLeast(1)
            bars[bar] = (ln(1f + avg * 0.45f) / 3.6f).coerceIn(0f, 1f)
        }

        val bass = bars.subList(0, 6).average().toFloat().coerceIn(0f, 1f)
        val mid = bars.subList(6, 15).average().toFloat().coerceIn(0f, 1f)
        val treble = bars.subList(15, 24).average().toFloat().coerceIn(0f, 1f)
        val level = (bass * 0.5f + mid * 0.35f + treble * 0.15f).coerceIn(0f, 1f)

        val now = System.currentTimeMillis()
        val isBeat = bass > (rollingBass * 1.42f + 0.08f) && (now - lastBeatTime) > 190L
        if (isBeat) lastBeatTime = now
        rollingBass = rollingBass * 0.88f + bass * 0.12f

        _audioFrame.value = AudioFrame(
            level = level,
            bass = bass,
            mid = mid,
            treble = treble,
            isBeat = isBeat,
            bars = bars
        )
    }

    private fun extractMetadata(uri: Uri): AudioTrackItem {
        var displayName = "Аудиотрек"
        var artist = "Неизвестный исполнитель"
        var duration = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIdx >= 0) {
                    displayName = cursor.getString(nameIdx)
                        .substringBeforeLast('.')
                        .ifBlank { displayName }
                }
            }
        }
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }?.let { displayName = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.let { duration = it }
            retriever.release()
        }
        return AudioTrackItem(
            uriString = uri.toString(),
            title = displayName,
            artist = artist,
            durationMs = duration
        )
    }
}
