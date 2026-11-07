package me.ct.group.listener;

import com.intellij.ide.AppLifecycleListener;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.updateSettings.impl.UpdateSettings;
import com.intellij.util.text.DateFormatUtil;
import me.ct.group.setting.Setting;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.Objects;

/**
 * 检查IDE和插件的更新
 */
public class CheckUpdate implements AppLifecycleListener {

    private static final Logger LOG = Logger.getInstance(CheckUpdate.class);
    
    private static final String LAST_CHECK_DATE_KEY = "me.ct.group.check.update.last.check.date";

    /**
     * 旧版IDE进入欢迎界面时触发；IDEA 2026.3 起欢迎界面改为“欢迎界面项目”，不会触发此方法，
     * 该场景由 {@link me.ct.group.activity.CheckUpdateActivity} 触发
     */
    @Override
    public void welcomeScreenDisplayed() {
        checkForUpdates();
    }

    /**
     * 检查更新，一天只检查一次；可能阻塞（含最长约10秒的网络探测），调用方决定是否放到后台线程
     */
    public static synchronized void checkForUpdates() {
        Setting.State state = Objects.requireNonNull(Setting.getInstance().getState());
        if (!state.checkUpdateStatus) {
            return;
        }
        
        String checkDate = DateFormatUtil.formatDate(new Date());
        PropertiesComponent propertiesComponent = PropertiesComponent.getInstance();
        String lastCheckDate = propertiesComponent.getValue(LAST_CHECK_DATE_KEY);
        if (checkDate.equals(lastCheckDate)) {
            LOG.info("今天已经进行IDE和插件的检查更新，跳过检查");
            return;
        }

        UpdateSettings updateSettings = UpdateSettings.getInstance();

        UpdateSettings updateSettingsCopy = new UpdateSettings();//不改动用户设置
        updateSettingsCopy.getState().copyFrom(updateSettings.getState());
        updateSettingsCopy.setCheckNeeded(true);
        updateSettingsCopy.setPluginsCheckNeeded(true);

        if (!isNetworkAvailable()) {
            LOG.warn("网络不可用，跳过更新检查，将在下次IDE启动时重试");
            return;
        }

        if (!triggerUpdateCheck(updateSettingsCopy)) {
            LOG.error("无法触发IDE和插件的检查更新");
            return;
        }
        propertiesComponent.setValue(LAST_CHECK_DATE_KEY, checkDate);
        LOG.info("进行IDE和插件的检查更新-成功");
    }

    /**
     * 优先通过反射调用 UpdateChecker.updateAndShowResult(Project, UpdateSettings) 并传入强制开启检查的
     * settings 副本，确保不受 IDEA 用户“自动检查更新”设置影响；2026.3 起 UpdateChecker 被移入 internal
     * 模块，插件类加载器不可见，需借用门面类的类加载器加载。门面接口内部使用全局 UpdateSettings，仅作兜底。
     * 均通过反射访问，避免插件字节码引用内部模块导致校验/加载问题。
     */
    @SuppressWarnings("unchecked")
    private static boolean triggerUpdateCheck(UpdateSettings updateSettingsCopy) {
        Class<?> facadeClass = null;
        Object facade = null;
        try {
            facadeClass = Class.forName("com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade");
            facade = ApplicationManager.getApplication().getService((Class) facadeClass);
        } catch (ClassNotFoundException ignored) {
            // 早于门面引入的版本（如 2024.3），下面用插件可见的类加载器调用旧接口
        } catch (Throwable e) {
            LOG.warn("获取UpdateCheckerFacade失败", e);
        }

        ClassLoader[] classLoaders = {
                facade != null ? facade.getClass().getClassLoader() : null,
                facadeClass != null ? facadeClass.getClassLoader() : null,
                UpdateSettings.class.getClassLoader()
        };
        for (ClassLoader classLoader : classLoaders) {
            if (classLoader == null) {
                continue;
            }
            try {
                Class<?> checkerClass = Class.forName("com.intellij.openapi.updateSettings.impl.UpdateChecker",
                        true, classLoader);
                Method updateAndShowResult = checkerClass.getMethod("updateAndShowResult", Project.class, UpdateSettings.class);
                updateAndShowResult.invoke(null, null, updateSettingsCopy);
                LOG.info("通过UpdateChecker(带设置)触发更新检查");
                return true;
            } catch (ClassNotFoundException ignored) {
                // 换下一个类加载器
            } catch (Throwable e) {
                LOG.warn("通过UpdateChecker(带设置)检查更新失败，将尝试其他方式", e);
            }
        }

        if (facade != null) {
            try {
                Method updateAndShowResult = facadeClass.getMethod("updateAndShowResult", Project.class);
                updateAndShowResult.invoke(facade, new Object[]{null});
                LOG.info("通过UpdateCheckerFacade触发更新检查");
                return true;
            } catch (Throwable e) {
                LOG.warn("通过UpdateCheckerFacade检查更新失败", e);
            }
        }
        return false;
    }

    /**
     * 通过尝试连接 JetBrains 插件仓库来检测网络连通性，
     * 避免 updateAndShowResult 内部异步吞掉异常导致误记录检查日期。
     */
    private static boolean isNetworkAvailable() {
        try {
            URL url = new URL("https://plugins.jetbrains.com");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("HEAD");
            int code = conn.getResponseCode();
            conn.disconnect();
            return code >= 200 && code < 400;
        } catch (IOException e) {
            return false;
        }
    }

}
