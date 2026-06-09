package com.littlemine.webbrowser.client.audio

import com.cinemamod.mcef.MCEF
import com.cinemamod.mcef.MCEFBrowser
import com.littlemine.webbrowser.block.MonitorBlockEntity
import com.littlemine.webbrowser.logic.MonitorShape
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.cef.browser.CefBrowser
import org.cef.handler.CefAudioHandler
import org.cef.misc.CefAudioParameters
import org.lwjgl.BufferUtils
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC11
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

private val LOGGER = com.mojang.logging.LogUtils.getLogger()

object MCEFAudioEngine : CefAudioHandler {

    private val browserToPos = ConcurrentHashMap<CefBrowser, BlockPos>()
    private val streams = ConcurrentHashMap<BlockPos, AudioStream>()
    private val pendingStart = ConcurrentLinkedQueue<Pair<BlockPos, PendingStreamInfo>>()
    private val pendingDrain = ConcurrentLinkedQueue<BlockPos>()
    private var registered = false

    fun register(pos: BlockPos, browser: MCEFBrowser) {
        browserToPos[browser] = pos
    }

    fun unregister(pos: BlockPos) {
        browserToPos.entries.removeIf { it.value == pos }
        streams.remove(pos)?.destroy()
    }

    private fun tryRegister() {
        if (registered) return
        try {
            val mcefClient = MCEF.getClient()
            if (mcefClient != null) {
                val cefClient = mcefClient.getHandle()
                if (cefClient != null) {
                    cefClient.addAudioHandler(this)
                    registered = true
                    LOGGER.info("[Audio] Registered CefAudioHandler with MCEF")
                }
            }
        } catch (e: Exception) {
            LOGGER.warn("[Audio] Failed to register audio handler (will retry)", e)
        }
    }

    fun tick() {
        tryRegister()
        if (!registered) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        if (ALC11.alcGetCurrentContext() == 0L) return

        while (true) {
            val ps = pendingStart.poll() ?: break
            val (pos, info) = ps
            streams.remove(pos)?.destroy()
            val be = level.getBlockEntity(pos)
            if (be !is MonitorBlockEntity) continue
            val stream = AudioStream(info.parameters, info.channels, be)
            streams[pos] = stream
            LOGGER.info("[Audio] Started stream for {} ({}ch, {}Hz)", pos, info.channels, info.parameters.sampleRate)
        }

        while (true) {
            val pos = pendingDrain.poll() ?: break
            streams[pos]?.drain()
        }

        val toRemove = mutableListOf<BlockPos>()
        for ((pos, stream) in streams) {
            val be = level.getBlockEntity(pos)
            if (be is MonitorBlockEntity) {
                stream.updateVolume(be.volume)
                stream.updateAudioMode(be.audioMode)
                stream.updateShape(be.shape)
            }
            stream.tick()
            if (stream.isFinished()) {
                toRemove.add(pos)
            }
        }
        for (pos in toRemove) {
            LOGGER.info("[Audio] Removing finished stream for {}", pos)
            streams.remove(pos)?.destroy()
        }
    }

    override fun getAudioParameters(browser: CefBrowser, parameters: CefAudioParameters): Boolean = true

    override fun onAudioStreamStarted(browser: CefBrowser, parameters: CefAudioParameters, channels: Int) {
        val pos = browserToPos[browser] ?: return
        pendingStart.add(pos to PendingStreamInfo(parameters, channels))
    }

    override fun onAudioStreamPacket(browser: CefBrowser, data: FloatArray, frames: Int, pts: Long) {
        val pos = browserToPos[browser] ?: return
        streams[pos]?.queuePacket(data, frames)
    }

    override fun onAudioStreamStopped(browser: CefBrowser) {
        val pos = browserToPos[browser] ?: return
        pendingDrain.add(pos)
    }

    override fun onAudioStreamError(browser: CefBrowser, message: String) {
        LOGGER.warn("[Audio] Stream error: {}", message)
        val pos = browserToPos[browser] ?: return
        pendingDrain.add(pos)
    }

    private data class PendingStreamInfo(val parameters: CefAudioParameters, val channels: Int)
}

private class AudioStream(params: CefAudioParameters, channels: Int, be: MonitorBlockEntity) {
    private val sampleRate = params.sampleRate
    private val channelCount = channels
    private val format = if (channels == 1) AL10.AL_FORMAT_MONO16 else AL10.AL_FORMAT_STEREO16
    private val source = AL11.alGenSources()
    private val buffers = mutableListOf<Int>()
    private val availableBuffers = ConcurrentLinkedQueue<Int>()
    private val packetQueue = ConcurrentLinkedQueue<QueuedPacket>()
    private var started = false
    private var draining = false
    private var volume: Int = be.volume
    private var audioMode: Int = be.audioMode
    private var centerPos: Triple<Float, Float, Float>? = null

    private val BUFFER_COUNT = 4

    init { updateShape(be.shape) }

    fun drain() { draining = true }

    fun updateVolume(v: Int) { volume = v }
    fun updateAudioMode(m: Int) { audioMode = m }
    fun updateShape(s: MonitorShape?) {
        centerPos = if (s != null) {
            val cx = s.topLeft.x.toFloat() + s.rightDir.stepX * (s.width / 2f) +
                    s.downDir.stepX * (s.height / 2f) + 0.5f
            val cy = s.topLeft.y.toFloat() + s.rightDir.stepY * (s.width / 2f) +
                    s.downDir.stepY * (s.height / 2f) + 0.5f
            val cz = s.topLeft.z.toFloat() + s.rightDir.stepZ * (s.width / 2f) +
                    s.downDir.stepZ * (s.height / 2f) + 0.5f
            Triple(cx, cy, cz)
        } else null
    }

    fun queuePacket(data: FloatArray, frames: Int) {
        if (draining) return
        packetQueue.add(QueuedPacket(data, frames))
    }

    fun tick() {
        if (buffers.isEmpty()) {
            for (i in 0 until BUFFER_COUNT) {
                val buf = AL11.alGenBuffers()
                buffers.add(buf)
                availableBuffers.add(buf)
            }
        }

        var processed = AL11.alGetSourcei(source, AL11.AL_BUFFERS_PROCESSED)
        while (processed > 0) {
            val buf = AL11.alSourceUnqueueBuffers(source)
            availableBuffers.add(buf)
            processed--
        }

        while (availableBuffers.isNotEmpty()) {
            val packet = packetQueue.poll() ?: break
            val buf = availableBuffers.poll()
            val byteData = convertTo16BitPCM(packet.data, packet.frames)
            AL11.alBufferData(buf, format, byteData, sampleRate)
            AL11.alSourceQueueBuffers(source, buf)
        }

        if (!started && AL11.alGetSourcei(source, AL11.AL_BUFFERS_QUEUED) > 0) {
            AL11.alSourcePlay(source)
            started = true
        }

        if (started) {
            val gain = (volume / 100f).coerceIn(0f, 3f)
            AL11.alSourcef(source, AL10.AL_GAIN, gain)

            if (audioMode == 0) {
                AL11.alSourcei(source, AL11.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
                AL11.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f)
            } else {
                AL11.alSourcei(source, AL11.AL_SOURCE_RELATIVE, AL10.AL_FALSE)
                val pos = centerPos
                if (pos != null) {
                    AL11.alSource3f(source, AL10.AL_POSITION, pos.first, pos.second, pos.third)
                }
            }
        }
    }

    fun isFinished(): Boolean = draining && packetQueue.isEmpty() && AL11.alGetSourcei(source, AL11.AL_BUFFERS_QUEUED) == 0

    fun destroy() {
        AL11.alSourceStop(source)
        val queued = AL11.alGetSourcei(source, AL11.AL_BUFFERS_QUEUED)
        for (i in 0 until queued) {
            AL11.alSourceUnqueueBuffers(source)
        }
        AL11.alDeleteSources(source)
        for (buf in buffers) {
            AL11.alDeleteBuffers(buf)
        }
        packetQueue.clear()
        draining = true
    }

    private fun convertTo16BitPCM(data: FloatArray, frames: Int): ByteBuffer {
        val totalSamples = frames * channelCount
        val buf = BufferUtils.createByteBuffer(totalSamples * 2)
        val shortBuf = buf.asShortBuffer()
        val limit = totalSamples.coerceAtMost(data.size)
        for (i in 0 until limit) {
            shortBuf.put((data[i] * 32767f).coerceIn(-32768f, 32767f).toInt().toShort())
        }
        buf.limit(totalSamples * 2)
        return buf
    }

    private data class QueuedPacket(val data: FloatArray, val frames: Int)
}
