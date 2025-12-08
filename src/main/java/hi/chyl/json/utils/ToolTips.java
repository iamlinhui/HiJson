package hi.chyl.json.utils;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;

/**
 * 桌面气泡通知/提示框工具类。
 * 使用 JWindow 实现非阻塞、带有动画效果的桌面通知。
 */
public class ToolTips {

    // --- 默认常量设定 ---
    private static final int DEFAULT_WIDTH = 300;
    private static final int DEFAULT_HEIGHT = 100;
    private static final int DEFAULT_STEP = 30;     // 动画步长 (像素)
    private static final int DEFAULT_STEP_TIME = 30; // 每步延迟时间 (毫秒)
    private static final int DEFAULT_DISPLAY_TIME = 3000; // 提示框显示时间 (毫秒)
    private static final int DEFAULT_GAP = 5; // 内部组件间距

    // --- 动画/显示属性 ---
    private int width = DEFAULT_WIDTH;
    private int height = DEFAULT_HEIGHT;
    private int step = DEFAULT_STEP;
    private int stepTime = DEFAULT_STEP_TIME;
    private int displayTime = DEFAULT_DISPLAY_TIME;

    // --- 计数器/状态管理 ---
    // 当前正在执行动画的气泡提示数量
    private volatile int countOfToolTip = 0;
    // 自启动以来出现过的最大气泡数量 (用于计算堆叠位置)
    private int maxToolTip = 0;
    // 在屏幕上最大可以显示的气泡提示数量 (基于屏幕高度)
    private int maxToolTipScreen;
    // 是否支持窗体置顶 (setAlwaysOnTop)
    private boolean useTop = true;

    // --- 样式属性 ---
    private Font font;
    private Color backgroundColor;
    private Color borderColor;
    private Color messageColor;
    private int gap = DEFAULT_GAP;

    /**
     * 构造函数，初始化默认气泡提示设置。
     */
    public ToolTips() {
        // 设定默认字体
        font = new Font("宋体", Font.PLAIN, 12);
        // 设定默认颜色
        backgroundColor = new Color(255, 255, 225); // 淡黄色
        borderColor = Color.BLACK;
        messageColor = Color.BLACK;

        // 尝试通过反射检查是否支持 setAlwaysOnTop (主要为 JRE 1.5 兼容性)
        try {
            JWindow.class.getMethod("setAlwaysOnTop", boolean.class);
        } catch (Exception e) {
            useTop = false;
        }
    }

    /**
     * 重构 JWindow 用于显示单一气泡提示框。
     */
    class ToolTipSingle extends JWindow {
        private static final long serialVersionUID = 1L;

        private final JLabel iconLabel = new JLabel();
        private final JTextArea messageArea = new JTextArea();

        public ToolTipSingle() {
            initComponents();
        }

        private void initComponents() {
            setSize(width, height);
            messageArea.setFont(getMessageFont());

            // 外部面板 (用于边框)
            JPanel externalPanel = new JPanel(new BorderLayout(1, 1));
            externalPanel.setBackground(backgroundColor);

            // 内部面板 (用于间距/Padding)
            JPanel innerPanel = new JPanel(new BorderLayout(gap, gap));
            innerPanel.setBackground(backgroundColor);

            // 消息区域配置
            messageArea.setBackground(backgroundColor);
            messageArea.setMargin(new Insets(4, 4, 4, 4));
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);
            messageArea.setForeground(messageColor);
            messageArea.setEditable(false); // 不允许编辑

            // 创建 EtchedBorder (浮雕化边框)
            Border etchedBorder = BorderFactory.createEtchedBorder();
            externalPanel.setBorder(etchedBorder);

            // 组装组件
            innerPanel.add(iconLabel, BorderLayout.WEST);
            innerPanel.add(messageArea, BorderLayout.CENTER);
            externalPanel.add(innerPanel);

            getContentPane().add(externalPanel);
        }

        /**
         * 启动动画。
         */
        public void animate() {
            new Animation(this).start();
        }
    }

    /**
     * 负责处理气泡提示框的动画和生命周期。
     * 注意：由于直接使用 Thread.sleep() 和操作 Swing 组件，
     * 此类应被视为在非 EDT 线程中执行，这在严格的 Swing 编程中需要谨慎。
     */
    class Animation extends Thread {

        private final ToolTipSingle singleWindow;

        public Animation(ToolTipSingle single) {
            this.singleWindow = single;
        }

        /**
         * 调用动画效果，垂直移动窗体坐标。
         *
         * @param posX   X坐标
         * @param startY 起始Y坐标
         * @param endY   目标Y坐标
         * @throws InterruptedException
         */
        private void animateVertically(int posX, int startY, int endY)
                throws InterruptedException {
            singleWindow.setLocation(posX, startY);
            if (endY < startY) { // 向上移动
                for (int i = startY; i > endY; i -= step) {
                    singleWindow.setLocation(posX, i);
                    Thread.sleep(stepTime);
                }
            } else { // 向下移动
                for (int i = startY; i < endY; i += step) {
                    singleWindow.setLocation(posX, i);
                    Thread.sleep(stepTime);
                }
            }
            // 确保最终位置准确
            singleWindow.setLocation(posX, endY);
        }

        /**
         * 开始动画处理和生命周期管理。
         */
        @Override
        public void run() {
            try {
                // 获取屏幕信息
                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
                Rectangle screenRect = ge.getMaximumWindowBounds(); // 考虑任务栏的安全区域
                int screenHeight = (int) screenRect.getHeight();
                int screenYStart = (int) screenRect.getY();

                // 屏幕顶端是否在 Y=0 之外 (例如任务栏在顶部)
                boolean animateFromBottom = (screenYStart == 0);

                maxToolTipScreen = screenHeight / height;
                int posX = (int) screenRect.getWidth() - width - 1;

                int startYPosition;
                int stopYPosition;

                if (animateFromBottom) {
                    // 从底部滑入
                    startYPosition = screenHeight;
                    // 计算目标位置，考虑堆叠
                    stopYPosition = startYPosition - height - 1;
                    if (countOfToolTip > 0) {
                        // 堆叠逻辑：根据当前最大气泡数计算偏移
                        stopYPosition -= (maxToolTip % maxToolTipScreen * height);
                    }
                } else {
                    // 从顶部滑入 (如果任务栏在顶部)
                    startYPosition = screenYStart - height;
                    stopYPosition = screenYStart;
                    if (countOfToolTip > 0) {
                        stopYPosition += (maxToolTip % maxToolTipScreen * height);
                    }
                }

                // 启动前更新计数器
                countOfToolTip++;
                maxToolTip++;

                // 窗体初始化和显示
                singleWindow.setLocation(posX, startYPosition);
                singleWindow.setVisible(true);
                if (useTop) {
                    singleWindow.setAlwaysOnTop(true);
                }

                // 动画：滑入
                animateVertically(posX, startYPosition, stopYPosition);

                // 保持显示
                Thread.sleep(displayTime);

                // 动画：滑出
                animateVertically(posX, stopYPosition, startYPosition);

            } catch (Exception e) {
                // 异常处理，例如中断，需要确保资源释放
                System.err.println("ToolTip Animation Error: " + e.getMessage());
            } finally {
                // 确保无论如何都释放资源和更新计数器
                countOfToolTip--;
                singleWindow.setVisible(false);
                singleWindow.dispose();
            }
        }
    }

    // --- 公共 API (对外接口) ---

    /**
     * 设定显示的图标及信息，并启动动画。
     *
     * @param icon 可选的显示图标。
     * @param msg  必填的消息内容。
     */
    public void setToolTip(Icon icon, String msg) {
        ToolTipSingle single = new ToolTipSingle();
        if (icon != null) {
            single.iconLabel.setIcon(icon);
        }
        single.messageArea.setText(msg);
        single.animate();
    }

    /**
     * 设定显示的信息（无图标），并启动动画。
     *
     * @param msg 必填的消息内容。
     */
    public void setToolTip(String msg) {
        setToolTip(null, msg);
    }

    // --- Getter 和 Setter 方法 (遵循标准 Java Bean 规范) ---

    public Font getMessageFont() {
        return font;
    }

    public void setMessageFont(Font font) {
        this.font = font;
    }

    public Color getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(Color borderColor) {
        this.borderColor = borderColor;
    }

    public int getDisplayTime() {
        return displayTime;
    }

    public void setDisplayTime(int displayTime) {
        this.displayTime = displayTime;
    }

    public int getGap() {
        return gap;
    }

    public void setGap(int gap) {
        this.gap = gap;
    }

    public Color getMessageColor() {
        return messageColor;
    }

    public void setMessageColor(Color messageColor) {
        this.messageColor = messageColor;
    }

    public int getStep() {
        return step;
    }

    public void setStep(int step) {
        this.step = step;
    }

    public int getStepTime() {
        return stepTime;
    }

    public void setStepTime(int stepTime) {
        this.stepTime = stepTime;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(Color bgColor) {
        this.backgroundColor = bgColor;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }
}
