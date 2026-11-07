package me.ct.group.activity

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.ct.group.listener.CheckUpdate

/**
 * 打开项目时触发更新检查：IDEA 2026.3 起欢迎界面改为“欢迎界面项目”，
 * 不再触发 AppLifecycleListener.welcomeScreenDisplayed()，由本类覆盖该场景（含启动时直接重开项目）；
 * 是否真正检查由 [CheckUpdate.checkForUpdates] 内部按天判重。
 */
class CheckUpdateActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // 检查更新含网络请求，放到 IO 线程，避免阻塞 EDT
        withContext(Dispatchers.IO) {
            CheckUpdate.checkForUpdates()
        }
    }

}
