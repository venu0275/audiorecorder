package com.audio.audiorecorder

import android.Manifest
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.audio.audiorecorder.databinding.ActivityRecordingsBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private lateinit var recordingsAdapter: RecordingsAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentPlayingPosition = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "My Recordings"

        setupRecyclerView()
        loadRecordings()
    }

    private fun setupRecyclerView() {
        recordingsAdapter = RecordingsAdapter { recording, position ->
            showRecordingOptions(recording, position)
        }

        binding.recyclerViewRecordings.apply {
            layoutManager = LinearLayoutManager(this@RecordingsActivity)
            adapter = recordingsAdapter
            addItemDecoration(SpacingItemDecoration(16))
        }
    }

    private fun loadRecordings() {
        val recordingsDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            "AudioRecordings"
        )

        if (!recordingsDir.exists()) {
            binding.tvEmptyState.visibility = View.VISIBLE
            return
        }

        // FIX: Changed emptyArray() to emptyList<File>() to match the List return type of sortedByDescending
        val recordings = recordingsDir.listFiles { file ->
            file.extension.lowercase(Locale.getDefault()) in listOf("m4a", "mp3", "wav", "aac")
        }?.sortedByDescending { it.lastModified() } ?: emptyList<File>()

        if (recordings.isEmpty()) {
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.tvEmptyState.visibility = View.GONE
            // recordings is already a List, so .toList() is no longer strictly necessary but kept for clarity if preferred
            recordingsAdapter.submitList(recordings)
        }
    }

    private fun showRecordingOptions(recording: File, position: Int) {
        val options = arrayOf("Play/Pause", "Share", "Rename", "Delete")

        AlertDialog.Builder(this)
            .setTitle("Select Action")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> togglePlayback(recording, position)
                    1 -> shareRecording(recording)
                    2 -> renameRecording(recording)
                    3 -> deleteRecording(recording, position)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun togglePlayback(recording: File, position: Int) {
        try {
            if (currentPlayingPosition == position && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                recordingsAdapter.notifyItemChanged(position)
            } else {
                if (mediaPlayer?.isPlaying == true) {
                    mediaPlayer?.stop()
                    mediaPlayer?.release()
                    recordingsAdapter.notifyItemChanged(currentPlayingPosition)
                }

                mediaPlayer = MediaPlayer().apply {
                    setDataSource(recording.absolutePath)
                    prepare()
                    start()

                    setOnCompletionListener {
                        currentPlayingPosition = -1
                        recordingsAdapter.notifyItemChanged(position)
                    }
                }

                currentPlayingPosition = position
                recordingsAdapter.notifyItemChanged(position)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error playing recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareRecording(recording: File) {
        val uri = FileProvider.getUriForFile(
            this,
            "${packageName}.fileprovider",
            recording
        )

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        startActivity(Intent.createChooser(shareIntent, "Share Recording"))
    }

    private fun renameRecording(recording: File) {
        val view = layoutInflater.inflate(R.layout.dialog_rename, null)
        val etName = view.findViewById<TextView>(R.id.etFileName)
        etName.text = recording.nameWithoutExtension

        AlertDialog.Builder(this)
            .setTitle("Rename Recording")
            .setView(view)
            .setPositiveButton("Save") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val newFile = File(recording.parent, "$newName.${recording.extension}")
                    if (recording.renameTo(newFile)) {
                        Toast.makeText(this, "Renamed successfully", Toast.LENGTH_SHORT).show()
                        loadRecordings()
                    } else {
                        Toast.makeText(this, "Failed to rename", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteRecording(recording: File, position: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete Recording")
            .setMessage("Are you sure you want to delete this recording?")
            .setPositiveButton("Delete") { _, _ ->
                if (recording.delete()) {
                    if (currentPlayingPosition == position) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        currentPlayingPosition = -1
                    }
                    Toast.makeText(this, "Recording deleted", Toast.LENGTH_SHORT).show()
                    loadRecordings()
                } else {
                    Toast.makeText(this, "Failed to delete", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    inner class RecordingsAdapter(
        private val onItemClick: (File, Int) -> Unit
    ) : RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

        private var recordings = listOf<File>()

        fun submitList(list: List<File>) {
            recordings = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_recording, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val recording = recordings[position]
            holder.bind(recording, position)
        }

        override fun getItemCount() = recordings.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvName: TextView = itemView.findViewById(R.id.tvFileName)
            private val tvDetails: TextView = itemView.findViewById(R.id.tvFileDetails)
            private val btnPlay: ImageButton = itemView.findViewById(R.id.btnPlay)

            fun bind(recording: File, position: Int) {
                tvName.text = recording.name

                val date = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                    .format(Date(recording.lastModified()))
                val size = String.format("%.2f MB", recording.length() / (1024.0 * 1024.0))
                tvDetails.text = "$date • $size"

                val isPlaying = currentPlayingPosition == position && mediaPlayer?.isPlaying == true
                btnPlay.setImageResource(
                    if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                )

                btnPlay.setOnClickListener {
                    onItemClick(recording, position)
                }

                itemView.setOnClickListener {
                    onItemClick(recording, position)
                }
            }
        }
    }
}