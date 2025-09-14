package com.example.privacy_guard_flutter

import android.media.Image
import java.nio.ByteBuffer

object CameraYuvHelper {
    fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 2
        val nv21 = ByteArray(ySize + uvSize)

        val yBuffer: ByteBuffer = image.planes[0].buffer
        val uBuffer: ByteBuffer = image.planes[1].buffer
        val vBuffer: ByteBuffer = image.planes[2].buffer

        val rowStrideY = image.planes[0].rowStride
        val rowStrideU = image.planes[1].rowStride
        val pixelStrideU = image.planes[1].pixelStride
        val rowStrideV = image.planes[2].rowStride
        val pixelStrideV = image.planes[2].pixelStride

        var pos = 0
        if (rowStrideY == width) {
            yBuffer.get(nv21, 0, ySize)
            pos += ySize
        } else {
            val yRow = ByteArray(rowStrideY)
            for (i in 0 until height) {
                yBuffer.get(yRow, 0, rowStrideY)
                System.arraycopy(yRow, 0, nv21, pos, width)
                pos += width
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val uRow = ByteArray(rowStrideU)
        val vRow = ByteArray(rowStrideV)

        for (r in 0 until chromaHeight) {
            vBuffer.get(vRow, 0, rowStrideV)
            uBuffer.get(uRow, 0, rowStrideU)
            var index = 0
            for (c in 0 until chromaWidth) {
                val v = vRow[index]
                val u = uRow[index]
                nv21[pos++] = v
                nv21[pos++] = u
                index += pixelStrideV
            }
        }

        return nv21
    }
}
