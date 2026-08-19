package kg.edu.tin.liangbuliang

import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

  @Test
  fun `slider 0 should map to brightness value 0 dot 1`() {
    val value = SettingsRepository.sliderPositionToBrightnessValue(0f)
    assertEquals(0.1f, value, 0.001f)
  }

  @Test
  fun `slider 0 dot 5 should map to brightness value 1`() {
    val value = SettingsRepository.sliderPositionToBrightnessValue(0.5f)
    assertEquals(1.0f, value, 0.001f)
  }

  @Test
  fun `slider 1 should map to brightness value 10`() {
    val value = SettingsRepository.sliderPositionToBrightnessValue(1f)
    assertEquals(10.0f, value, 0.001f)
  }

  @Test
  fun `brightness value 0 dot 1 should map to slider 0`() {
    val position = SettingsRepository.brightnessValueToSliderPosition(0.1f)
    assertEquals(0f, position, 0.001f)
  }

  @Test
  fun `brightness value 1 should map to slider 0 dot 5`() {
    val position = SettingsRepository.brightnessValueToSliderPosition(1.0f)
    assertEquals(0.5f, position, 0.001f)
  }

  @Test
  fun `brightness value 10 should map to slider 1`() {
    val position = SettingsRepository.brightnessValueToSliderPosition(10.0f)
    assertEquals(1f, position, 0.001f)
  }

  @Test
  fun `slider to brightness roundtrip should be consistent`() {
    // Roundtrip is now exact (continuous float mapping, no quantization).
    for (slider in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
      val value = SettingsRepository.sliderPositionToBrightnessValue(slider)
      val backToSlider = SettingsRepository.brightnessValueToSliderPosition(value)
      assertEquals(slider, backToSlider, 0.001f)
    }
  }

  @Test
  fun `brightness value 1 should map to system value 1 (absolute minimum)`() {
    assertEquals(1, SettingsRepository.brightnessToSystemValue(1.0f))
  }

  @Test
  fun `brightness value 10 should map to system value 10`() {
    assertEquals(10, SettingsRepository.brightnessToSystemValue(10.0f))
  }

  @Test
  fun `sub 1 brightness should map to system value 1 (minimum)`() {
    assertEquals(1, SettingsRepository.brightnessToSystemValue(0.3f))
  }

  @Test
  fun `extra dim level for 0 dot 1 should be 1`() {
    assertEquals(1f, SettingsRepository.extraDimLevelFor(0.1f), 0.001f)
  }

  @Test
  fun `extra dim level for 1 should be 0`() {
    assertEquals(0f, SettingsRepository.extraDimLevelFor(1.0f), 0.001f)
  }

  @Test
  fun `extra dim level above 1 should be 0`() {
    assertEquals(0f, SettingsRepository.extraDimLevelFor(5.0f), 0.001f)
  }

  @Test
  fun `formatBrightnessValue shows a whole number at or above 1`() {
    assertEquals("1%", SettingsRepository.formatBrightnessValue(1.0f))
    assertEquals("5%", SettingsRepository.formatBrightnessValue(5.0f))
    assertEquals("10%", SettingsRepository.formatBrightnessValue(10.0f))
  }

  @Test
  fun `formatBrightnessValue floors sub-1 to 0 dot 1 without a 1 dot 0 duplicate`() {
    assertEquals("0.1%", SettingsRepository.formatBrightnessValue(0.1f))
    assertEquals("0.5%", SettingsRepository.formatBrightnessValue(0.5f))
    assertEquals("0.9%", SettingsRepository.formatBrightnessValue(0.9f))
    assertEquals("0.9%", SettingsRepository.formatBrightnessValue(0.95f))
    assertEquals("0.9%", SettingsRepository.formatBrightnessValue(0.99f))
  }
}
