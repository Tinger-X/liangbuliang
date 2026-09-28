package kg.edu.tin.liangbuliang

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

  @Test
  fun `parses the version name produced by the build script`() {
    val version = AppVersion.parse("v26.08.r145")
    assertEquals(26, version?.year)
    assertEquals(8, version?.month)
    assertEquals(145, version?.release)
  }

  @Test
  fun `parses release ids padded to two digits`() {
    assertEquals(5, AppVersion.parse("v26.09.r05")?.release)
  }

  @Test
  fun `rejects malformed versions`() {
    assertNull(AppVersion.parse("26.08.145"))
    assertNull(AppVersion.parse("v26.08.145"))
    assertNull(AppVersion.parse("v26.08.r"))
    assertNull(AppVersion.parse(null))
    assertNull(AppVersion.parse(""))
  }

  @Test
  fun `compares release ids within the same month`() {
    assertTrue(AppVersion.parse("v26.08.r146")!! > AppVersion.parse("v26.08.r145")!!)
    assertFalse(AppVersion.parse("v26.08.r145")!! > AppVersion.parse("v26.08.r146")!!)
  }

  @Test
  fun `compares by year then month then release id, matching versionCode order`() {
    // 与 build.gradle.kts 的 versionCode 拼接顺序一致：26.08.r145 -> 2608145
    assertTrue(AppVersion.parse("v26.09.r01")!! > AppVersion.parse("v26.08.r145")!!)
    assertTrue(AppVersion.parse("v27.01.r01")!! > AppVersion.parse("v26.12.r99")!!)
  }

  @Test
  fun `release id reset at the new year does not look like a downgrade`() {
    // 跨年时发布序号归 1，靠年段更高来保证单调性
    assertTrue(AppVersion.parse("v27.01.r01")!! > AppVersion.parse("v26.12.r50")!!)
  }

  @Test
  fun `equal versions are not newer`() {
    assertFalse(AppVersion.isNewer("v26.08.r145", "v26.08.r145"))
  }

  @Test
  fun `isNewer detects a newer server version`() {
    assertTrue(AppVersion.isNewer("v26.08.r146", "v26.08.r145"))
    assertFalse(AppVersion.isNewer("v26.08.r144", "v26.08.r145"))
  }

  @Test
  fun `isNewer falls back to string comparison when a version is unparseable`() {
    // 服务端文件名不按约定命名时，退化为「不同即提示」，但相同仍然不提示
    assertTrue(AppVersion.isNewer("v26.9", "v26.08.r145"))
    assertFalse(AppVersion.isNewer("weird-build", "weird-build"))
  }

  @Test
  fun `blank server version never reports an update`() {
    assertFalse(AppVersion.isNewer(null, "v26.08.r145"))
    assertFalse(AppVersion.isNewer("", "v26.08.r145"))
  }
}
