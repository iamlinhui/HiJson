package hi.chyl.json;

import org.jdesktop.application.Action;
import org.jdesktop.application.Application;
import org.jdesktop.application.ResourceMap;

import javax.swing.*;
import java.awt.*;

/**
 * 关于对话框 (About Box) 类。
 * 依赖于 JDesktop Application Framework (AppFramework) 进行资源和动作绑定。
 */
public class MainAboutBox extends JDialog {

    // --- 成员变量 ---
    private JButton closeButton;

    /**
     * 构造函数。
     *
     * @param parent 父 Frame 窗体。
     */
    public MainAboutBox(Frame parent) {
        super(parent);
        initComponents();
        // 设置关闭按钮为默认按钮，使用户按 Enter 键可关闭对话框。
        getRootPane().setDefaultButton(closeButton);
    }

    /**
     * 关闭“关于”对话框的 Action。
     */
    @Action
    public void closeAboutBox() {
        dispose();
    }

    /**
     * 初始化窗体组件和布局。
     * WARNING: 此方法内容由 GUI 设计器生成，不建议手动修改。
     */
    private void initComponents() {

        closeButton = new JButton();
        // 组件声明
        JLabel appTitleLabel = new JLabel();
        JLabel versionLabel = new JLabel();
        JLabel appVersionLabel = new JLabel();
        JLabel vendorLabel = new JLabel();
        JLabel appVendorLabel = new JLabel();
        JLabel homepageLabel = new JLabel();
        JLabel appHomepageLabel = new JLabel();
        JLabel appDescLabel = new JLabel();
        JLabel imageLabel = new JLabel();
        JLabel homepageLabel1 = new JLabel();
        JLabel appHomepageLabel1 = new JLabel();

        // --- 窗体基本设置 ---
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        // 通过 JDesktop AppFramework 的 ResourceMap 获取资源
        ResourceMap resourceMap = Application.getInstance(MainApp.class).getContext().getResourceMap(MainAboutBox.class);
        setTitle(resourceMap.getString("title"));
        setModal(true);
        setName("aboutBox");
        setResizable(false);

        // --- 动作绑定 ---
        ActionMap actionMap = Application.getInstance(MainApp.class).getContext().getActionMap(MainAboutBox.class, this);
        closeButton.setAction(actionMap.get("closeAboutBox"));
        closeButton.setName("closeButton");

        // --- 标签内容和样式设置（从 ResourceMap 读取） ---
        appTitleLabel.setFont(appTitleLabel.getFont().deriveFont(appTitleLabel.getFont().getStyle() | Font.BOLD, appTitleLabel.getFont().getSize() + 4));
        appTitleLabel.setText(resourceMap.getString("Application.title"));
        appTitleLabel.setName("appTitleLabel");

        versionLabel.setFont(versionLabel.getFont().deriveFont(versionLabel.getFont().getStyle() | Font.BOLD));
        versionLabel.setText(resourceMap.getString("versionLabel.text"));
        versionLabel.setName("versionLabel");

        appVersionLabel.setText(resourceMap.getString("Application.version"));
        appVersionLabel.setName("appVersionLabel");

        vendorLabel.setFont(vendorLabel.getFont().deriveFont(vendorLabel.getFont().getStyle() | Font.BOLD));
        vendorLabel.setText(resourceMap.getString("vendorLabel.text"));
        vendorLabel.setName("vendorLabel");

        appVendorLabel.setText(resourceMap.getString("Application.vendor"));
        appVendorLabel.setName("appVendorLabel");

        homepageLabel.setFont(homepageLabel.getFont().deriveFont(homepageLabel.getFont().getStyle() | Font.BOLD));
        homepageLabel.setText(resourceMap.getString("homepageLabel.text"));
        homepageLabel.setName("homepageLabel");

        appHomepageLabel.setText(resourceMap.getString("Application.homepage"));
        appHomepageLabel.setName("appHomepageLabel");

        appDescLabel.setText(resourceMap.getString("appDescLabel.text"));
        appDescLabel.setName("appDescLabel");

        imageLabel.setIcon(resourceMap.getIcon("imageLabel.icon"));
        imageLabel.setName("imageLabel");

        homepageLabel1.setFont(homepageLabel1.getFont().deriveFont(homepageLabel1.getFont().getStyle() | Font.BOLD));
        homepageLabel1.setText(resourceMap.getString("homepageLabel1.text"));
        homepageLabel1.setName("homepageLabel1");

        appHomepageLabel1.setText(resourceMap.getString("appHomepageLabel1.text"));
        appHomepageLabel1.setName("appHomepageLabel1");

        // --- GroupLayout 布局定义 ---
        GroupLayout layout = new GroupLayout(getContentPane());
        getContentPane().setLayout(layout);

        // 水平布局
        layout.setHorizontalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addGroup(layout.createSequentialGroup()
                                .addComponent(imageLabel)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(18, 18, 18)
                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                        .addComponent(appDescLabel, GroupLayout.DEFAULT_SIZE, 266, Short.MAX_VALUE)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                        .addComponent(versionLabel)
                                                                        .addComponent(vendorLabel)
                                                                        .addComponent(homepageLabel))
                                                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                                                                        .addComponent(appVersionLabel)
                                                                        .addComponent(appVendorLabel)
                                                                        .addComponent(appHomepageLabel, GroupLayout.PREFERRED_SIZE, 102, GroupLayout.PREFERRED_SIZE)))
                                                        .addComponent(appTitleLabel)
                                                        .addGroup(layout.createSequentialGroup()
                                                                .addComponent(homepageLabel1)
                                                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(appHomepageLabel1, GroupLayout.DEFAULT_SIZE, 220, Short.MAX_VALUE))))
                                        .addGroup(layout.createSequentialGroup()
                                                .addGap(106, 106, 106)
                                                .addComponent(closeButton)))
                                .addContainerGap())
        );

        // 垂直布局
        layout.setVerticalGroup(
                layout.createParallelGroup(GroupLayout.Alignment.LEADING)
                        .addComponent(imageLabel, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addGroup(layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(appTitleLabel)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(appDescLabel, GroupLayout.PREFERRED_SIZE, 24, GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(versionLabel)
                                        .addComponent(appVersionLabel))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(vendorLabel)
                                        .addComponent(appVendorLabel))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(homepageLabel)
                                        .addComponent(appHomepageLabel, GroupLayout.PREFERRED_SIZE, 15, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(layout.createParallelGroup(GroupLayout.Alignment.BASELINE)
                                        .addComponent(homepageLabel1)
                                        .addComponent(appHomepageLabel1, GroupLayout.PREFERRED_SIZE, 15, GroupLayout.PREFERRED_SIZE))
                                .addPreferredGap(LayoutStyle.ComponentPlacement.UNRELATED)
                                .addComponent(closeButton)
                                .addGap(1, 1, 1))
        );

        pack();
    }

}
