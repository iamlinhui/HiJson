package hi.chyl.json;

import org.jdesktop.application.Application;
import org.jdesktop.application.SingleFrameApplication;

import java.awt.*;

/**
 * 应用程序的主类，用于启动和管理应用的生命周期。
 * <p>
 * 注意：本类继承自 {@code SingleFrameApplication}，完全依赖于已停止维护的
 * JDesktop Application Framework (AppFramework)。
 */
public class MainApp extends SingleFrameApplication {

    /**
     * 在应用程序启动时创建并显示主框架（MainView）。
     *
     * @Override
     */
    @Override
    protected void startup() {
        // 创建并显示应用的主视图（通常是一个 JFrame）
        show(new MainView(this));
    }

    /**
     * 配置主窗口。
     * AppFramework 允许在此处注入资源到窗口中。
     * 由于 MainView 通常已由 GUI 构建器完全初始化，此方法通常留空。
     *
     * @param root 应用程序的根窗口。
     * @Override
     */
    @Override
    protected void configureWindow(Window root) {
        // 无需额外配置
    }

    /**
     * 获取应用程序实例的便捷静态方法 (单例模式)。
     *
     * @return {@code MainApp} 的当前实例。
     */
    public static MainApp getApplication() {
        return Application.getInstance(MainApp.class);
    }

    /**
     * 应用程序的主入口方法，启动 AppFramework。
     *
     * @param args 命令行参数。
     */
    public static void main(String[] args) {
        // 委托给 AppFramework 的 launch 方法来初始化和启动应用生命周期
        launch(MainApp.class, args);
    }
}
