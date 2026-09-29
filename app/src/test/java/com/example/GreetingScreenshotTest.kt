package com.example

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.core.youtube.Class12Filter
import com.example.core.youtube.YouTubeDiscoveryService
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun testClass12Filter() {
    // Valid Class 12 variations
    assertTrue(Class12Filter.isClass12Content("Class 12 Hindi - संपूर्ण काव्य खंड Revision"))
    assertTrue(Class12Filter.isClass12Content("Physics Electrostatics Class XII Live"))
    assertTrue(Class12Filter.isClass12Content("12th Board Exam 2025 Model Paper Solution"))
    assertTrue(Class12Filter.isClass12Content("Bihar Board 12 Chemistry One Shot"))
    assertTrue(Class12Filter.isClass12Content("12वीं हिंदी व्याकरण महा मैराथन"))

    // Non-Class 12 or conflicting classes must be rejected
    assertFalse(Class12Filter.isClass12Content("Class 10 Hindi Chapter 1 Live"))
    assertFalse(Class12Filter.isClass12Content("General Physics for Beginners"))
    assertFalse(Class12Filter.isClass12Content("Matric Exam Tips 2025"))
    assertFalse(Class12Filter.isClass12Content("Class 11 Chemistry Live Session"))
  }

  @Test
  fun testExactChannelIdentification() {
    val ch1 = YouTubeDiscoveryService.PRECONFIGURED_CHANNELS[0]
    val ch2 = YouTubeDiscoveryService.PRECONFIGURED_CHANNELS[1]

    assertEquals("UCNsmL3gvYyL8nPA3w6QuZ6g", ch1.channelId)
    assertTrue(ch1.channelId.startsWith("UC"))
    assertEquals(24, ch1.channelId.length)

    assertEquals("UCvaUdm8gLsPUD1PD8N8qr8w", ch2.channelId)
    assertTrue(ch2.channelId.startsWith("UC"))
    assertEquals(24, ch2.channelId.length)
  }

  @Test
  fun testIsoTimestampParsing() {
    val iso = "2026-09-28T14:30:00Z"
    val millis = YouTubeDiscoveryService.parseIsoTimestamp(iso)
    assertNotNull(millis)
    assertTrue(millis!! > 0)
  }

  @Test
  fun greeting_screenshot() {
    composeTestRule.setContent {
      MyApplicationTheme {
        Text("SK AI Smart Study Planner - Upcoming Classes")
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }
}
