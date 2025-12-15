package `fun`.pc4.catvideo

import android.app.Activity
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.nio.ByteBuffer
import android.content.ContentValues
import android.provider.MediaStore


class MainActivity : AppCompatActivity() {

    private val PICK_VIDEOS = 100
    private val selectedVideos = mutableListOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val selectButton = findViewById<Button>(R.id.selectVideosButton)
        val statusText = findViewById<TextView>(R.id.statusText)

        selectButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "video/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            startActivityForResult(intent, PICK_VIDEOS)
        }
        // Ko-fi donation link
        val donateLink = findViewById<TextView>(R.id.donateLink)
        donateLink.setOnClickListener {
            val browserIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://ko-fi.com/nathanaelnewton")
            )
            startActivity(browserIntent)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_VIDEOS && resultCode == Activity.RESULT_OK) {
            selectedVideos.clear()

            data?.let {
                if (it.clipData != null) {
                    val count = it.clipData!!.itemCount
                    for (i in 0 until count) {
                        selectedVideos.add(it.clipData!!.getItemAt(i).uri)
                    }
                } else if (it.data != null) {
                    selectedVideos.add(it.data!!)
                }
            }

            if (selectedVideos.size > 1) {
                val statusTextView = findViewById<TextView>(R.id.statusText)
                statusTextView.text = "Concatenating..."

                Thread {
                    concatVideos(selectedVideos) { progress ->
                        runOnUiThread {
                            statusTextView.text = progress
                        }
                    }
                }.start()
            }
        }
    }

    private fun getFreeSpaceMb(): Long {
        val path = getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: return 0
        return path.freeSpace / (1024 * 1024)
    }

    private fun concatVideos(uris: List<Uri>, progressCallback: (String) -> Unit): Boolean {
        if (uris.isEmpty()) return false

        try {
            val filename = "output_${System.currentTimeMillis()}.mp4"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
            }
            val outputUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: return false
            val pfd = contentResolver.openFileDescriptor(outputUri, "rw") ?: return false

            val muxer = MediaMuxer(pfd.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            // 1️⃣ Get total duration and track info
            var totalDurationUs = 0L
            val durations = mutableListOf<Long>()
            var rotationDegrees = 0
            for ((index, uri) in uris.withIndex()) {
                val extractor = MediaExtractor()
                extractor.setDataSource(this, uri, null)
                val format = extractor.getTrackFormat(0)
                if (index == 0) {
                    rotationDegrees = format.getInteger("rotation-degrees", 0)
                }
                val durationUs = format.getLong(MediaFormat.KEY_DURATION)
                durations.add(durationUs)
                totalDurationUs += durationUs
                extractor.release()
            }

            // 2️⃣ Add tracks from first video
            val firstExtractor = MediaExtractor()
            firstExtractor.setDataSource(this, uris[0], null)
            var videoTrackIndex = -1
            var audioTrackIndex = -1
            for (i in 0 until firstExtractor.trackCount) {
                val format = firstExtractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("video/")) videoTrackIndex = muxer.addTrack(format)
                if (mime.startsWith("audio/")) audioTrackIndex = muxer.addTrack(format)
            }
            firstExtractor.release()

            // 3️⃣ Set rotation for correct orientation
            muxer.setOrientationHint(rotationDegrees)
            muxer.start()

            // 4️⃣ Concatenate all videos
            var videoOffsetUs = 0L
            var audioOffsetUs = 0L
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()
            var processedDurationUs = 0L
            val startTime = System.currentTimeMillis()

            for ((index, uri) in uris.withIndex()) {
                val extractor = MediaExtractor()
                extractor.setDataSource(this, uri, null)

                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    val muxerTrack = when {
                        mime.startsWith("video/") -> videoTrackIndex
                        mime.startsWith("audio/") -> audioTrackIndex
                        else -> continue
                    }
                    extractor.selectTrack(i)

                    while (true) {
                        val sampleSize = extractor.readSampleData(buffer, 0)
                        if (sampleSize < 0) break

                        bufferInfo.size = sampleSize
                        bufferInfo.offset = 0
                        bufferInfo.flags = extractor.sampleFlags
                        bufferInfo.presentationTimeUs = if (muxerTrack == videoTrackIndex)
                            extractor.sampleTime + videoOffsetUs
                        else
                            extractor.sampleTime + audioOffsetUs

                        muxer.writeSampleData(muxerTrack, buffer, bufferInfo)
                        extractor.advance()

                        if (muxerTrack == videoTrackIndex) {
                            val currentProgress = processedDurationUs + extractor.sampleTime
                            val percent = (currentProgress.toDouble() / totalDurationUs * 100).toInt()
                            val elapsed = (System.currentTimeMillis() - startTime) / 1000
                            val estimatedTotal = (elapsed / (currentProgress.toDouble() / totalDurationUs)).toInt()
                            val remaining = estimatedTotal - elapsed
                            val freeSpace = getFreeSpaceMb()
                            progressCallback("Progress: $percent% | Free space: ${freeSpace}MB | Remaining: ${remaining}s")
                        }
                    }
                }

                videoOffsetUs += extractor.getTrackFormat(0).getLong(MediaFormat.KEY_DURATION)
                if (audioTrackIndex != -1 && extractor.trackCount > 1) {
                    audioOffsetUs += extractor.getTrackFormat(1).getLong(MediaFormat.KEY_DURATION)
                }
                processedDurationUs += durations[index]
                extractor.release()
            }

            muxer.stop()
            muxer.release()
            pfd.close()
            progressCallback("Done! Saved to camera roll.")
            return true

        } catch (e: Exception) {
            e.printStackTrace()
            progressCallback("Error concatenating videos.")
            return false
        }
    }
}