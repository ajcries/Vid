package com.blue.ytdlpcommander

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.blue.ytdlpcommander.service.DownloadService

class ShareReceiverActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
        val url = sharedText?.let { extractUrl(it) }

        if (url != null) {
            DownloadService.enqueue(this, url)
            Toast.makeText(this, "Added to downloads", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "No link found in what was shared", Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun extractUrl(text: String): String? {
        val regex = Regex("""https?://\S+""")
        return regex.find(text)?.value
    }
}
