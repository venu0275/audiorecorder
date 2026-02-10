package com.audio.audiorecorder

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.audio.audiorecorder.databinding.ActivityRecordingsBinding
import java.io.File

class RecordingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingsBinding
    private val adapter = RecordingsAdapter { file, action -> onFileAction(file, action) }
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        // Ensure SpacingItemDecoration.kt exists in your project, or remove this line
        if (runCatching { Class.forName("com.audio.audiorecorder.SpacingItemDecoration") }.isSuccess) {
            binding.recyclerView.addItemDecoration(SpacingItemDecoration(16))
        }
        binding.recyclerView.adapter = adapter

        loadRecordings()

        binding.btnBack.setOnClickListener { finish() }
    }

    private fun loadRecordings() {
        val dir = getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val files = dir?.listFiles()?.filter { it.extension == "wav" || it.extension == "m4a" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()

        adapter.submitList(files)
        binding.tvEmpty.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun onFileAction(file: File, action: Action) {
        when (action) {
            Action.PLAY -> playAudio(file)
            Action.SHARE -> shareAudio(file)
            Action.DELETE -> {
                if (file.delete()) {
                    Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show()
                    loadRecordings()
                }
            }
        }
    }

    private fun playAudio(file: File) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener { stopAudio() }
            }
            Toast.makeText(this, "Playing: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Playback failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopAudio() {
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun shareAudio(file: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share Recording"))
        } catch (e: Exception) {
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }

    enum class Action { PLAY, SHARE, DELETE }
}

class RecordingsAdapter(private val onClick: (File, RecordingsActivity.Action) -> Unit) :
    RecyclerView.Adapter<RecordingsAdapter.ViewHolder>() {

    private var items: List<File> = emptyList()

    fun submitList(files: List<File>) {
        items = files
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recording, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val file = items[position]
        holder.tvName.text = file.name

        val sizeMb = file.length() / (1024.0 * 1024.0)
        holder.tvDate.text = "%.2f MB".format(sizeMb)

        holder.btnPlay.setOnClickListener { onClick(file, RecordingsActivity.Action.PLAY) }
        holder.btnShare.setOnClickListener { onClick(file, RecordingsActivity.Action.SHARE) }
        holder.btnDelete.setOnClickListener { onClick(file, RecordingsActivity.Action.DELETE) }
    }

    override fun getItemCount() = items.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvDate: TextView = view.findViewById(R.id.tvDate)
        val btnPlay: ImageButton = view.findViewById(R.id.btnPlay)
        val btnShare: ImageButton = view.findViewById(R.id.btnShare)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
    }
}