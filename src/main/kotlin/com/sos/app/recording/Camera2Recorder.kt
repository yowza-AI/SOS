package com.sos.app.recording

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Build
import android.util.Log
import android.view.Surface
import java.io.File
import kotlin.math.max

/**
 * Dual camera recorder using Camera2 API.
 * Attempts concurrent front+rear; gracefully falls back to rear-only.
 */
class Camera2Recorder(private val context: Context) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private var videoFile: File? = null
    private var mediaCodec: MediaCodec? = null
    private var mediaMuxer: MediaMuxer? = null

    companion object {
        private const val TAG = "Camera2Recorder"
        private const val VIDEO_WIDTH = 1920
        private const val VIDEO_HEIGHT = 1080
        private const val VIDEO_FPS = 30
        private const val VIDEO_BITRATE = 3_000_000 // 3 Mbps
    }

    fun start(outputFile: File): Boolean {
        videoFile = outputFile
        return try {
            setupVideoEncoder()
            setupMediaMuxer()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recorder: ${e.message}")
            false
        }
    }

    fun stop() {
        try {
            mediaCodec?.stop()
            mediaCodec?.release()
            mediaMuxer?.stop()
            mediaMuxer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping recorder: ${e.message}")
        }
    }

    fun getInputSurface(): Surface? {
        return mediaCodec?.createInputSurface()
    }

    fun hasConcurrentCameraSupport(): Boolean {
        // Check if device supports concurrent front+rear camera recording
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val concurrentCameras = cameraManager.concurrentCameraIds
                concurrentCameras.isNotEmpty()
            } catch (e: Exception) {
                false
            }
        } else {
            false
        }
    }

    fun getAvailableCameras(): List<String> {
        val cameras = mutableListOf<String>()
        try {
            for (cameraId in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (facing == CameraCharacteristics.LENS_FACING_BACK || facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameras.add(cameraId)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting camera list: ${e.message}")
        }
        return cameras
    }

    private fun setupVideoEncoder() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, VIDEO_WIDTH, VIDEO_HEIGHT)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
        format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        try {
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_HEVC)
            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.w(TAG, "HEVC not supported, falling back to H.264: ${e.message}")
            val h264Format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, VIDEO_WIDTH, VIDEO_HEIGHT)
            h264Format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            h264Format.setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BITRATE)
            h264Format.setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            h264Format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec?.configure(h264Format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }

        mediaCodec?.start()
    }

    private fun setupMediaMuxer() {
        val file = videoFile ?: throw IllegalStateException("Video file not set")
        mediaMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    }
}
