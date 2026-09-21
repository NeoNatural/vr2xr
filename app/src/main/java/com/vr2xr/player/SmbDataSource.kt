package com.vr2xr.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import com.vr2xr.smb.RandomAccessSource
import com.vr2xr.smb.SmbClientManager
import com.vr2xr.smb.NativeSmbRandomAccessSource
import java.io.EOFException
import java.io.IOException

class SmbDataSource(
    private val manager: SmbClientManager,
    private val profileId: String
) : BaseDataSource(false) {
    private var source: RandomAccessSource? = null
    private var openedUri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var opened = false
    private val readAheadBuffer = ByteArray(CONTINUOUS_READ_SIZE_BYTES)
    private var bufferedPosition = 0L
    private var bufferedLength = 0
    private var sequentialRefills = 0

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        val uri = dataSpec.uri
        try {
            val randomAccess = openRandomAccess(uri)
            val size = try {
                randomAccess.size()
            } catch (error: Throwable) {
                randomAccess.close()
                throw error
            }
            if (dataSpec.position > size) {
                randomAccess.close()
                throw EOFException("SMB read position exceeds file size")
            }
            source = randomAccess
            openedUri = uri
            readPosition = dataSpec.position
            bufferedPosition = dataSpec.position
            bufferedLength = 0
            sequentialRefills = 0
            bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                size - dataSpec.position
            } else {
                dataSpec.length.coerceAtMost(size - dataSpec.position)
            }
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (error: IOException) {
            throw error
        } catch (error: Throwable) {
            throw IOException("Unable to open SMB media", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        if (!isBuffered(readPosition)) {
            refillBuffer()
        }
        val bufferOffset = (readPosition - bufferedPosition).toInt()
        val available = bufferedLength - bufferOffset
        if (available <= 0) return C.RESULT_END_OF_INPUT
        val copied = minOf(length, available, bytesRemaining.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        readAheadBuffer.copyInto(
            destination = buffer,
            destinationOffset = offset,
            startIndex = bufferOffset,
            endIndex = bufferOffset + copied
        )
        readPosition += copied
        bytesRemaining -= copied
        bytesTransferred(copied)
        return copied
    }

    override fun getUri(): Uri? = openedUri

    override fun close() {
        openedUri = null
        readPosition = 0L
        bytesRemaining = 0L
        bufferedLength = 0
        sequentialRefills = 0
        val current = source
        source = null
        val wasOpened = opened
        opened = false
        try {
            current?.close()
        } finally {
            if (wasOpened) transferEnded()
        }
    }

    private fun openRandomAccess(uri: Uri): RandomAccessSource {
        val profile = manager.profileFor(profileId)
        return try {
            NativeSmbRandomAccessSource.open(profile, uri.toString()).also {
                Log.i(TAG, "Native libsmb2 video source opened")
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Native SMB2 open failed; using jcifs fallback: ${error.message}")
            manager.openRandomAccess(profileId, uri.toString())
        }
    }

    private fun isBuffered(position: Long): Boolean =
        position >= bufferedPosition && position < bufferedPosition + bufferedLength

    private fun refillBuffer() {
        val wasSequential = bufferedLength > 0 && readPosition == bufferedPosition + bufferedLength
        sequentialRefills = if (wasSequential) sequentialRefills + 1 else 0
        val targetSize = if (sequentialRefills >= 1) {
            CONTINUOUS_READ_SIZE_BYTES
        } else {
            PROBE_READ_SIZE_BYTES
        }
        val requested = minOf(targetSize.toLong(), bytesRemaining).toInt()
        val read = source?.readAt(readPosition, readAheadBuffer, 0, requested)
            ?: throw IOException("SMB source is not open")
        if (read == 0) throw IOException("SMB source returned an empty read")
        bufferedPosition = readPosition
        bufferedLength = read.coerceAtLeast(0)
    }

    companion object {
        private const val TAG = "SmbDataSource"
        internal const val PROBE_READ_SIZE_BYTES = 128 * 1024
        internal const val CONTINUOUS_READ_SIZE_BYTES = 1024 * 1024
    }
}
