package com.social_downloader.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class NavigationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun entireActiveTabContainsLabelAtCompactWidthAndSelectionChanges() {
        val selected = mutableStateOf(Tab.HOME)
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp)) {
                    AnyDownNavigationBar(selected.value, 2) { selected.value = it }
                }
            }
        }
        Tab.entries.forEach { tab ->
            val surface = compose.onNodeWithTag("tab-surface-${tab.name}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val label = compose.onNodeWithText(tab.label, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("${tab.label} label inside highlight", surface.contains(label.topLeft) && surface.contains(label.bottomRight))
        }
        compose.onNodeWithText("Settings").performClick()
        compose.runOnIdle { assertEquals(Tab.SETTINGS, selected.value) }
        compose.onNode(isSelected()).assertTextContains("Settings")
        compose.onNodeWithText("Queue").performClick()
        compose.runOnIdle { assertEquals(Tab.QUEUE, selected.value) }
        compose.onNode(isSelected()).assertTextContains("Queue")
    }
}
