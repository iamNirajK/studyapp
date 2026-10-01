package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.settings.SettingsManager
import com.example.core.settings.UserProfile
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Niraj", appName)
  }

  @Test
  fun `test first-time user profile persistence and verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val settingsManager = SettingsManager(context)

    // Clear any previous state
    settingsManager.resetUserProfile()
    assertFalse(settingsManager.isSetupCompleted)
    assertNull(settingsManager.getUserProfile())

    // 1. Save profile with Telegram enabled
    val profile = UserProfile(
      setupCompleted = true,
      name = "Rahul Sharma",
      dateOfBirth = "2006-08-14",
      telegramEnabled = true,
      telegramChatId = "123456789",
      telegramBotToken = "987654321:AAFakeBotTokenTest"
    )

    val saved = settingsManager.saveUserProfile(profile)
    assertTrue("Profile save and verification must succeed", saved)
    assertTrue(settingsManager.isSetupCompleted)

    // Verify stored data
    val loaded = settingsManager.getUserProfile()
    assertNotNull(loaded)
    assertEquals("Rahul Sharma", loaded?.name)
    assertEquals("2006-08-14", loaded?.dateOfBirth)
    assertTrue(loaded?.telegramEnabled == true)
    assertEquals("123456789", loaded?.telegramChatId)
    assertEquals("987654321:AAFakeBotTokenTest", loaded?.telegramBotToken)

    // 2. Simulate app close & reopen with a new SettingsManager instance
    val freshInstanceAfterAppRestart = SettingsManager(context)
    assertTrue("Setup must remain completed on app restart", freshInstanceAfterAppRestart.isSetupCompleted)
    val restoredProfile = freshInstanceAfterAppRestart.getUserProfile()
    assertNotNull(restoredProfile)
    assertEquals("Rahul Sharma", restoredProfile?.name)
    assertEquals("2006-08-14", restoredProfile?.dateOfBirth)

    // 3. Test Reset Profile / Clear Local Data
    val resetResult = freshInstanceAfterAppRestart.resetUserProfile()
    assertTrue(resetResult)
    assertFalse("Setup completion must be false after reset", freshInstanceAfterAppRestart.isSetupCompleted)
    assertNull(freshInstanceAfterAppRestart.getUserProfile())

    // 4. Test profile with Telegram disabled
    val noTgProfile = UserProfile(
      setupCompleted = true,
      name = "Niraj",
      dateOfBirth = "2005-12-01",
      telegramEnabled = false
    )
    val savedNoTg = settingsManager.saveUserProfile(noTgProfile)
    assertTrue(savedNoTg)
    val loadedNoTg = settingsManager.getUserProfile()
    assertNotNull(loadedNoTg)
    assertEquals("Niraj", loadedNoTg?.name)
    assertFalse(loadedNoTg!!.telegramEnabled)
    assertNull(loadedNoTg.telegramChatId)
    assertNull(loadedNoTg.telegramBotToken)
  }
}

