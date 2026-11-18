package com.oyayasu.studysh

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oyayasu.studysh.supertonic.SupertonicAssets

@Composable
fun SupertonicVoiceSettings(speech: SupertonicSpeech) {
    val models = speech.models
    Text("日本語 · Supertonic 3", style = MaterialTheme.typography.titleLarge)
    Text("Japanese speech runs entirely on this device. Download the shared models once (about 399 MB) and choose a female voice. Additional profiles are about 0.3 MB each.",
        style = MaterialTheme.typography.bodySmall)
    Text(models.status, style = MaterialTheme.typography.bodySmall)
    if (models.busy) {
        if (models.totalBytes > 0L) {
            LinearProgressIndicator(progress = { models.progress }, modifier = Modifier.fillMaxWidth())
            Text("${models.downloadedBytes / 1_000_000} / ${models.totalBytes / 1_000_000} MB · ${(models.progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = models::cancel) { Text("Pause download") }
        } else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    models.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    SupertonicAssets.voices.forEach { voice ->
        val name = SupertonicAssets.voiceName(voice)
        val installed = models.installed(voice)
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("$name · Female · Japanese", style = MaterialTheme.typography.titleMedium)
                Text(if (installed) "Installed · Offline ready" else "Not installed", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed) {
                        Button(onClick = { speech.select(voice) }, enabled = speech.activeVoice != voice) {
                            Text(if (speech.activeVoice == voice) "Selected" else "Select")
                        }
                        OutlinedButton(onClick = { speech.preview(voice) }, enabled = !speech.busy) { Text("Test voice") }
                    } else {
                        Button(onClick = { models.install(voice) }, enabled = !models.busy) {
                            Text(if (models.coreInstalled()) "Download $name profile" else "Download $name + models")
                        }
                    }
                }
            }
        }
    }
    if (speech.busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedButton(onClick = speech::stop) { Text("Stop audio") }
    }
    if (speech.message.isNotBlank()) Text(speech.message, style = MaterialTheme.typography.bodySmall)
    speech.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}
