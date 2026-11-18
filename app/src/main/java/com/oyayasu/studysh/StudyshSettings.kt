package com.oyayasu.studysh

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class SettingsWindow { Settings, Voices }

@Composable fun StudyshWindow(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 680.dp).fillMaxWidth(.95f).fillMaxHeight(.9f),
            shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.background) { content() }
    }
}

@Composable fun StudyshSettings(appLanguage: String, studyLanguage: String, compact: Boolean,
    sounds: StudyshSounds, onAppLanguage: (String) -> Unit, onStudyLanguage: (String) -> Unit,
    onCompact: (Boolean) -> Unit, onVoices: () -> Unit, onClose: () -> Unit) {
    StudyshWindow(onClose) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui("Settings"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, "Close settings") }
            }
            Text(ui("App language"), style = MaterialTheme.typography.titleMedium)
            listOf("en" to "English", "pt" to "Português", "es" to "Español", "ja" to "日本語").forEach { (code, label) ->
                SettingsChoice(label, code == appLanguage, "app-language-$code") { onAppLanguage(code) }
            }
            HorizontalDivider()
            Text(ui("Language to study"), style = MaterialTheme.typography.titleMedium)
            Text(ui("Choose the language used by new study prompts. Existing lessons remain unchanged."), style = MaterialTheme.typography.bodySmall)
            StudyLanguage.supported.forEach { language ->
                SettingsChoice(ui(language), language == studyLanguage, "study-language-$language") { onStudyLanguage(language) }
            }
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui("Compact layout"), modifier = Modifier.weight(1f))
                Switch(compact, onCompact)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(ui("Sound effects"))
                    Text(ui("Quiet cartoon sounds during practice"), style = MaterialTheme.typography.bodySmall)
                }
                Switch(sounds.enabled, sounds::changeEnabled, modifier = Modifier.testTag("sound-effects"))
            }
            OutlinedButton(onClick = onVoices, modifier = Modifier.fillMaxWidth().testTag("settings-voices")) {
                Icon(Icons.Outlined.RecordVoiceOver, null)
                Text(" " + ui("Voice"))
            }
        }
    }
}

@Composable private fun SettingsChoice(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag(tag), shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFF1EAFB) else Color.White, border = BorderStroke(1.dp, Color(0xFFE4DEEB))) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            if (selected) Icon(Icons.Outlined.CheckCircle, "Selected", tint = Color(0xFF956DCE))
        }
    }
}
