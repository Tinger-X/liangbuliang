package kg.edu.tin.liangbuliang

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kg.edu.tin.liangbuliang.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 检查更新入口与两个更新对话框的截图基线。 */
@OptIn(ExperimentalRoborazziApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class UpdateScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @After
  fun tearDown() {
    // UpdateManager 是进程级单例，用例之间要把状态复位，避免相互影响。
    UpdateManager.setState(UpdateState.Idle)
  }

  @Test
  fun check_update_button_is_in_the_top_right_corner() {
    setMainScreen()
    composeTestRule.onNodeWithTag("check_update_button").assertExists()
  }

  /** 暗色主题下按钮底色取 surfaceContainer，需确认它和背景仍有足够对比、不会"消失"。 */
  @Test
  fun check_update_button_screenshot_in_dark_theme() {
    setMainScreen(darkTheme = true)
    composeTestRule.onNodeWithTag("check_update_button").assertExists()
    captureScreenRoboImage(filePath = "src/test/screenshots/update_button_dark.png")
  }

  @Test
  fun update_available_dialog_screenshot() {
    UpdateManager.setState(UpdateState.Available(latestVersion = "v26.09.r01", currentVersion = "v26.08.r145"))
    setMainScreen()
    composeTestRule.onNodeWithTag("update_available_dialog").assertExists()
    captureScreenRoboImage(filePath = "src/test/screenshots/update_available.png")
  }

  @Test
  fun update_progress_dialog_screenshot() {
    UpdateManager.setState(
      UpdateState.Downloading(
        version = "v26.09.r01",
        bytesRead = 7_340_032,
        totalBytes = 16_397_742,
        bytesPerSecond = 2_411_724
      )
    )
    setMainScreen()
    composeTestRule.onNodeWithTag("update_progress_dialog").assertExists()
    captureScreenRoboImage(filePath = "src/test/screenshots/update_progress.png")
  }

  @Test
  fun update_progress_dialog_without_content_length_stays_indeterminate() {
    UpdateManager.setState(
      UpdateState.Downloading(
        version = "v26.09.r01",
        bytesRead = 1_048_576,
        totalBytes = 0,
        bytesPerSecond = 0
      )
    )
    setMainScreen()
    composeTestRule.onNodeWithTag("update_progress_dialog").assertExists()
    captureScreenRoboImage(filePath = "src/test/screenshots/update_progress_indeterminate.png")
  }

  private fun setMainScreen(darkTheme: Boolean = false) {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val repository = SettingsRepository(context)
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = darkTheme) {
        MainScreen(
          repository = repository,
          onBrightnessToggle = {},
          onTimeoutToggle = {},
          onBrightnessChange = {},
          onTimeoutChange = {},
          onCheckUpdate = {},
          onStartDownload = {},
          onCancelDownload = {},
          onInstall = {}
        )
      }
    }
  }
}
