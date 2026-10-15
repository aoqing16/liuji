package com.liuji.xiaoqi.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 生成启动 + 看板/分类滚动路径的 Baseline Profile。
 *
 * 用法（真机已连接）:
 *   ./gradlew :app:generateBaselineProfile
 *
 * 若卡在用量权限页，请先在系统设置里授予「使用情况访问」，再重跑。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        device.wait(Until.hasObject(By.pkg(PACKAGE_NAME).depth(0)), 8_000)

        // 权限页：尽量点一次刷新，失败则继续（可能已有权限）
        device.findObject(By.text("刷新权限"))?.click()
        device.waitForIdle()

        // 底部导航：日 → 分类 → 再回日，覆盖 Dashboard / Categories
        clickTab("分类")
        scrollMainList()
        clickTab("日")
        scrollMainList()
        clickTab("周")
        device.waitForIdle()
        clickTab("月")
        device.waitForIdle()
        clickTab("分类")
        scrollMainList()
    }

    private fun MacrobenchmarkScope.clickTab(label: String) {
        val tab = device.wait(Until.findObject(By.text(label)), 3_000) ?: return
        tab.click()
        device.waitForIdle()
    }

    private fun MacrobenchmarkScope.scrollMainList() {
        val list = device.findObject(By.scrollable(true)) ?: return
        repeat(2) {
            runCatching { list.scroll(Direction.DOWN, 0.8f) }
            device.waitForIdle()
        }
        runCatching { list.scroll(Direction.UP, 0.8f) }
        device.waitForIdle()
    }

    companion object {
        private const val PACKAGE_NAME = "com.liuji.xiaoqi"
    }
}
