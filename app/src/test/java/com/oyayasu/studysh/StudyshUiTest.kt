package com.oyayasu.studysh

import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp")
class StudyshUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private var history = "{}"
    private var studyLanguage = "English"

    private fun entry(language: String, term: String, answer: String) = JSONObject()
        .put("term", term).put("language", language)
        .put("blocks", JSONArray().put(JSONObject().put("title", "Example")
            .put("examples", JSONArray().put(JSONObject().put("en", "学校で日本語を勉強します。")
                .put("pt", "Estudo japonês na escola.")))))
        .put("exercises", JSONArray().put(JSONObject().put("type", "choice")
            .put("id", "meaning-1").put("skillId", "meaning").put("instruction", "Choose the word")
            .put("options", JSONArray(listOf(answer, "other"))).put("answer", answer)))

    private val library get() = JSONObject().put("schemaVersion", "3.0").put("entries", JSONArray()
        .put(entry("English", "book", "a book"))
        .put(entry("Japanese", "学校", "学校"))).toString()

    @Before fun resetPreferences() {
        compose.activity.getSharedPreferences("studysh_ui", Context.MODE_PRIVATE).edit().clear().commit()
        StudyshI18n.language = "en"
    }

    private fun openApp(language: String = "English") {
        studyLanguage = language
        compose.setContent {
            StudyshApp(read = { library }, save = {}, readProgress = { history }, saveProgress = { history = it },
                readLanguage = { studyLanguage }, saveLanguage = { studyLanguage = it },
                readAppLanguage = { "en" }, saveAppLanguage = {})
        }
    }

    @Test fun settingsAreAvailableInEveryTabAndVoicesOpenInTheirOwnWindow() {
        openApp()
        compose.onNodeWithText("Voice").assertDoesNotExist()
        listOf("home", "create", "lessons", "practice").forEach { tab ->
            compose.onNodeWithTag("nav-$tab").performClick()
            compose.onNodeWithTag("global-settings").assertIsDisplayed().performClick()
            compose.onNodeWithTag("settings-voices").performScrollTo().performClick()
            compose.onNodeWithText("Voice settings").assertIsDisplayed()
            compose.onNodeWithTag("settings-voices").assertDoesNotExist()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Close settings").performClick()
        }
    }

    @Test fun theDownloadSymbolImportsAndTheUploadSymbolExportsWithoutMoving() {
        openApp()
        val importer = compose.onNodeWithTag("import-json")
        val exporter = compose.onNodeWithTag("export-backup")
        assertTrue(importer.fetchSemanticsNode().boundsInRoot.left < exporter.fetchSemanticsNode().boundsInRoot.left)
        importer.performClick()
        assertEquals(Intent.ACTION_GET_CONTENT, Shadows.shadowOf(compose.activity).nextStartedActivityForResult.intent.action)
        exporter.performClick()
        assertEquals(Intent.ACTION_CREATE_DOCUMENT, Shadows.shadowOf(compose.activity).nextStartedActivityForResult.intent.action)
    }

    @Test fun japaneseLessonShowsTheFuriganaToggleEvenWhenStudyingEnglishAndRendersRuby() {
        openApp()
        compose.onNodeWithTag("furigana-toggle").assertDoesNotExist()
        compose.onNodeWithTag("nav-lessons").performClick()
        compose.onNodeWithText("学校").performClick()
        compose.onNodeWithTag("furigana-toggle").assertIsDisplayed().performClick()
        compose.waitUntil(10_000) {
            descendants(compose.activity.window.decorView).filterIsInstance<TextView>().any { view ->
                val text = view.text
                text is Spanned && text.getSpans(0, text.length, FuriganaSpan::class.java).isNotEmpty()
            }
        }
        assertTrue(compose.activity.getSharedPreferences("studysh_ui", Context.MODE_PRIVATE).getBoolean("furigana", false))
        compose.onNodeWithTag("nav-home").performClick()
        compose.onNodeWithTag("furigana-toggle").assertDoesNotExist()
    }

    @Test fun japaneseStudyModeMakesFuriganaAvailableInEveryTab() {
        openApp("Japanese")
        listOf("home", "create", "lessons", "practice").forEach { tab ->
            compose.onNodeWithTag("nav-$tab").performClick()
            compose.onNodeWithTag("furigana-toggle").assertIsDisplayed()
            compose.onNodeWithTag("global-settings").assertIsDisplayed()
        }
    }

    @Test fun practiceSelectsOnlyThatLanguagesQuestionsAndStoresItsOwnHistory() {
        openApp()
        compose.onNodeWithTag("nav-practice").performClick()
        compose.onNodeWithText("Start practice").performScrollTo().performClick()
        compose.onNodeWithText("a book").performClick()
        compose.onNodeWithText("Check").performScrollTo().performClick()
        compose.onNodeWithText("Correct!").assertExists()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.runOnIdle {
            assertTrue(JSONObject(history).has("en::book:meaning"))
            assertFalse(JSONObject(history).has("ja::学校:meaning"))
        }
        compose.onNodeWithText("Back to Practice").performClick()
        compose.onNodeWithTag("practice-language-ja").performClick()
        compose.onNodeWithTag("furigana-toggle").assertIsDisplayed()
        compose.onNodeWithText("Start practice").performScrollTo().performClick()
        compose.onNodeWithText("a book").assertDoesNotExist()
        compose.onAllNodesWithText("学校").onFirst().assertExists()
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
}
