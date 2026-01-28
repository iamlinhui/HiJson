package hi.chyl.json.service;

import com.google.gson.JsonElement;
import hi.chyl.json.utils.NodeKit;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.netbeans.swing.tabcontrol.DefaultTabDataModel;
import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabDataModel;
import org.netbeans.swing.tabcontrol.TabbedContainer;
import org.netbeans.swing.tabcontrol.event.ComplexListDataEvent;
import org.netbeans.swing.tabcontrol.event.ComplexListDataListener;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 标签页管理服务
 * 负责标签页的创建、关闭和组件获取
 */
public class TabManager {

    private TabDataModel tabDataModel;
    private TabbedContainer tabbedContainer;
    private final Map<Integer, JsonElement> jsonEleTreeMap = new HashMap<>();

    // 标签选择变化监听器
    private Consumer<Void> tabSelectionListener;

    /**
     * 初始化标签页容器
     */
    public TabbedContainer init() {
        TabData tabData = createTabData("Welcome!", "This is a Tab!", null);
        tabDataModel = new DefaultTabDataModel(new TabData[]{tabData});
        tabbedContainer = new TabbedContainer(tabDataModel, TabbedContainer.TYPE_EDITOR);
        tabbedContainer.setForeground(new Color(238, 238, 238));
        tabbedContainer.getSelectionModel().setSelectedIndex(0);
        tabbedContainer.setShowCloseButton(true);

        // 监听标签页移除事件，清理 jsonEleTreeMap
        tabDataModel.addComplexListDataListener(new ComplexListDataListener() {
            public void indicesAdded(ComplexListDataEvent clde) {}
            public void indicesRemoved(ComplexListDataEvent clde) {}
            public void indicesChanged(ComplexListDataEvent clde) {}
            public void intervalAdded(ListDataEvent e) {}
            public void contentsChanged(ListDataEvent e) {}

            public void intervalRemoved(ListDataEvent e) {
                if (e instanceof ComplexListDataEvent) {
                    TabData[] tbArr = ((ComplexListDataEvent) e).getAffectedItems();
                    if (tbArr != null && tbArr.length > 0) {
                        JTree tree = getTree(tbArr[0]);
                        if (tree != null) {
                            jsonEleTreeMap.remove(tree.hashCode());
                        }
                    }
                }
            }
        });

        // 监听标签选择变化
        tabbedContainer.addActionListener(e -> {
            if ("select".equalsIgnoreCase(e.getActionCommand())) {
                if (tabSelectionListener != null) {
                    tabSelectionListener.accept(null);
                }
            }
        });

        return tabbedContainer;
    }

    /**
     * 设置标签选择变化监听器
     */
    public void setTabSelectionListener(Consumer<Void> listener) {
        this.tabSelectionListener = listener;
    }

    /**
     * 添加新标签页
     *
     * @param tabName  标签名称
     * @param isSelect 是否选中新标签
     * @return 新标签的索引
     */
    public int addTab(String tabName, boolean isSelect) {
        TabData tabData = createTabData(tabName, tabName, null);
        int newIndex = tabbedContainer.getTabCount();
        tabDataModel.addTab(newIndex, tabData);
        if (isSelect) {
            tabbedContainer.getSelectionModel().setSelectedIndex(newIndex);
        }
        return newIndex;
    }

    /**
     * 关闭当前标签页
     */
    public void closeCurrentTab() {
        int selIndex = getSelectedIndex();
        if (selIndex >= 0) {
            tabDataModel.removeTab(selIndex);
        }
    }

    /**
     * 修改标签名称
     */
    public void setTabName(int index, String name) {
        if (index >= 0 && index < tabDataModel.size()) {
            tabDataModel.setText(index, name);
        }
    }

    /**
     * 获取当前选中的标签索引
     */
    public int getSelectedIndex() {
        return tabbedContainer.getSelectionModel().getSelectedIndex();
    }

    /**
     * 获取当前标签的文本区域
     */
    public JTextArea getTextArea() {
        int selIndex = getSelectedIndex();
        if (selIndex >= 0) {
            TabData selTabData = tabDataModel.getTab(selIndex);
            JSplitPane selSplitPane = (JSplitPane) selTabData.getComponent();
            JScrollPane sp = (JScrollPane) selSplitPane.getLeftComponent();
            return (JTextArea) sp.getViewport().getView();
        }
        return null;
    }

    /**
     * 获取当前标签的树组件
     */
    public JTree getTree() {
        return getTree(getSelectedIndex());
    }

    /**
     * 获取指定标签的树组件
     */
    public JTree getTree(int tabIndex) {
        if (tabIndex < 0) {
            return null;
        }
        return getTree(tabDataModel.getTab(tabIndex));
    }

    /**
     * 从 TabData 获取树组件
     */
    public JTree getTree(TabData tabData) {
        if (tabData == null) {
            return null;
        }
        JSplitPane selSplitPane = (JSplitPane) tabData.getComponent();
        JSplitPane rightSplitPane = (JSplitPane) selSplitPane.getRightComponent();
        JScrollPane sp = (JScrollPane) rightSplitPane.getLeftComponent();
        return (JTree) sp.getViewport().getView();
    }

    /**
     * 获取当前标签的表格组件
     */
    public JTable getTable() {
        int index = getSelectedIndex();
        if (index < 0) {
            return null;
        }
        TabData selTabData = tabDataModel.getTab(index);
        JSplitPane selSplitPane = (JSplitPane) selTabData.getComponent();
        JSplitPane rightSplitPane = (JSplitPane) selSplitPane.getRightComponent();
        JScrollPane sp = (JScrollPane) rightSplitPane.getRightComponent();
        return (JTable) sp.getViewport().getView();
    }

    /**
     * 获取当前标签的主分割面板
     */
    public JSplitPane getSplitPane() {
        int selIndex = getSelectedIndex();
        if (selIndex < 0) {
            return null;
        }
        TabData selTabData = tabDataModel.getTab(selIndex);
        return (JSplitPane) selTabData.getComponent();
    }

    /**
     * 存储树对应的 JsonElement
     */
    public void putJsonElement(JTree tree, JsonElement jsonElement) {
        if (tree != null) {
            jsonEleTreeMap.put(tree.hashCode(), jsonElement);
        }
    }

    /**
     * 获取树对应的 JsonElement
     */
    public JsonElement getJsonElement(JTree tree) {
        if (tree == null) {
            return null;
        }
        return jsonEleTreeMap.get(tree.hashCode());
    }

    /**
     * 获取标签页容器
     */
    public TabbedContainer getTabbedContainer() {
        return tabbedContainer;
    }

    /**
     * 创建标签数据
     */
    private TabData createTabData(String tabName, String tabTip, Icon icon) {
        JSplitPane splitPane = new JSplitPane();
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.45);
            }
        });

        RSyntaxTextArea textArea = createTextArea();
        RTextScrollPane sp = new RTextScrollPane(textArea);
        sp.setFoldIndicatorEnabled(true);
        splitPane.setLeftComponent(sp);

        JSplitPane rightSplitPane = new JSplitPane();
        rightSplitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = rightSplitPane.getWidth();
                rightSplitPane.setDividerLocation(w > 500 ? (w - 220) / (float) w : 0.8f);
            }
        });

        JTree tree = createTree();
        rightSplitPane.setLeftComponent(new JScrollPane(tree));
        JTable table = createTable();
        rightSplitPane.setRightComponent(new JScrollPane(table));

        splitPane.setRightComponent(rightSplitPane);
        return new TabData(splitPane, icon, tabName, tabTip);
    }

    /**
     * 创建文本编辑区域
     */
    private RSyntaxTextArea createTextArea() {
        RSyntaxTextArea textArea = new RSyntaxTextArea();
        textArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_JAVASCRIPT);
        textArea.setCodeFoldingEnabled(true);
        textArea.setAntiAliasingEnabled(true);
        textArea.setAutoscrolls(true);

        SyntaxScheme scheme = textArea.getSyntaxScheme();
        scheme.getStyle(Token.LITERAL_STRING_DOUBLE_QUOTE).foreground = Color.BLUE;
        scheme.getStyle(Token.LITERAL_NUMBER_DECIMAL_INT).foreground = new Color(164, 0, 0);
        scheme.getStyle(Token.LITERAL_NUMBER_FLOAT).foreground = new Color(164, 0, 0);
        scheme.getStyle(Token.LITERAL_BOOLEAN).foreground = Color.RED;
        scheme.getStyle(Token.OPERATOR).foreground = Color.BLACK;

        textArea.revalidate();
        return textArea;
    }

    /**
     * 创建树组件
     */
    private JTree createTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("o-JSON");
        DefaultTreeModel model = new DefaultTreeModel(root);
        return new JTree(model);
    }

    /**
     * 创建表格组件
     */
    private JTable createTable() {
        String[] col = {"key", "value"};
        DefaultTableModel tm = new DefaultTableModel(col, 0);
        JTable table = new JTable(tm);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoscrolls(true);
        table.setMinimumSize(new Dimension(160, 100));
        return table;
    }
}
