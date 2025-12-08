package hi.chyl.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.gson.*;
import hi.chyl.json.utils.JsonFilter;
import hi.chyl.json.utils.NodeKit;
import hi.chyl.json.utils.ToolTips;
import hi.chyl.json.utils.ValueParser;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rsyntaxtextarea.SyntaxScheme;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rtextarea.RTextScrollPane;
import org.jdesktop.application.Application;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.netbeans.swing.tabcontrol.DefaultTabDataModel;
import org.netbeans.swing.tabcontrol.TabData;
import org.netbeans.swing.tabcontrol.TabDataModel;
import org.netbeans.swing.tabcontrol.TabbedContainer;
import org.netbeans.swing.tabcontrol.event.ComplexListDataEvent;
import org.netbeans.swing.tabcontrol.event.ComplexListDataListener;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class MainView extends FrameView {

    // --- Constants & Resources ---
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final char DOT = 30;

    // 预加载图标，避免在 Renderer 中重复加载导致性能问题
    private final Map<String, Icon> iconCache = new HashMap<>();

    // --- Components ---
    private JDialog aboutBox;
    private TabDataModel tabDataModel;
    private TabbedContainer tabbedContainer;
    private final Map<Integer, JsonElement> jsonEleTreeMap = new HashMap<>();

    // --- State ---
    private boolean isTxtFindDlgOpen = false;
    private boolean isTreeFinDlgOpen = false;
    private final List<TreePath> treePathLst = new ArrayList<>();
    private int curPos = 0;
    private final ResourceMap resourceMap;

    public MainView(SingleFrameApplication app) {
        super(app);
        resourceMap = Application.getInstance(MainApp.class).getContext().getResourceMap(MainView.class);
        preloadIcons();
        initUI();
    }

    private void preloadIcons() {
        String[] icons = {"json", "a", "v", "o", "n", "k"};
        for (String name : icons) {
            String path = "/images/" + name + (name.equals("json") ? ".png" : ".gif");
            iconCache.put(name, new ImageIcon(Objects.requireNonNull(getClass().getResource(path))));
        }
    }

    private void initUI() {
        // 安全地获取图标，防止 NPE
        Icon icon = iconCache.get("json");
        if (icon != null) {
            getFrame().setIconImage(((ImageIcon) icon).getImage());
        }

        setToolBar(createToolBar());
        setMenuBar(createMenuBar());
        initTabbedContainer();
        setComponent(tabbedContainer);
    }

    // --- Toolbar Construction ---

    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        JTextField textField = new JTextField();
        textField.setMaximumSize(new Dimension(180, 100));

        // 使用 Helper 方法减少重复代码
        toolbar.add(createToolbarButton("新标签(N)", e -> addTab("NewTab", true)));
        toolbar.add(createToolbarButton("关闭标签(W)", e -> closeCurrentTab()));
        toolbar.add(createToolbarButton("格式化(F)", e -> formatJson()));
        toolbar.add(createToolbarButton("排序(G)", e -> sortFormatJson()));
        toolbar.add(createToolbarButton("压缩(H)", e -> zipFormatJson()));
        toolbar.add(createToolbarButton("去空(B)", e -> filterFormatJson()));
        toolbar.add(createToolbarButton("解析(X)", e -> deepParseFormatJson()));
        toolbar.add(createToolbarButton("清空(D)", e -> Optional.ofNullable(getTextArea()).ifPresent(ta -> ta.setText(""))));
        toolbar.add(createToolbarButton("粘帖(V)", e -> Optional.ofNullable(getTextArea()).ifPresent(ta -> {
            ta.paste();
            formatJson();
        })));
        toolbar.add(createToolbarButton("清除(\\n)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\n", "")))));
        toolbar.add(createToolbarButton("清除(\\)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\\\\", "")))));
        toolbar.add(createToolbarButton("节点查找", e -> {
            if (!isTreeFinDlgOpen) {
                showFindDialog(2, "树节点查找对话框");
            }
        }));
        toolbar.add(createToolbarButton("文本查找", e -> {
            if (!isTxtFindDlgOpen) {
                showFindDialog(1, "文本查找对话框");
            }
        }));

        toolbar.addSeparator(new Dimension(30, 20));
        toolbar.add(textField);
        JButton btnSelTabName = new JButton("标签名修改");
        btnSelTabName.addActionListener(e -> {
            int selIndex = getTabIndex();
            if (selIndex >= 0) {
                tabDataModel.setText(selIndex, textField.getText());
            }
        });
        toolbar.add(btnSelTabName);
        return toolbar;
    }

    private JButton createToolbarButton(String text, ActionListener action) {
        JButton btn = new JButton(text);
        btn.addActionListener(action);
        return btn;
    }

    // --- Menu Construction ---

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setName("menuBar");

        // File Menu
        JMenu fileMenu = createMenu("fileMenu");
        fileMenu.add(createMenuItem("menuItemOpenFile", KeyEvent.VK_O, e -> openFileAction(getTextArea())));
        fileMenu.add(createMenuItem("menuItemSaveFile", KeyEvent.VK_S, e -> saveFileAction(getTextArea())));

        JMenuItem exitMenuItem = new JMenuItem();
        ActionMap actionMap = Application.getInstance(MainApp.class).getContext().getActionMap(MainView.class, this);
        exitMenuItem.setAction(actionMap.get("quit"));
        exitMenuItem.setText(resourceMap.getString("exitMenu.text"));
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);

        // Edit Menu
        JMenu editMenu = createMenu("editMenu");
        editMenu.add(createMenuItem("menuItemClean", KeyEvent.VK_D, e -> modifyText(ta -> ta.setText(""))));
        editMenu.add(createMenuItem("menuItemFormat", KeyEvent.VK_F, e -> formatJson()));
        editMenu.add(createMenuItem("menuItemSortFormat", KeyEvent.VK_G, e -> sortFormatJson()));
        editMenu.add(createMenuItem("menuItemZip", KeyEvent.VK_H, e -> zipFormatJson()));
        editMenu.add(createMenuItem("menuItemFilter", KeyEvent.VK_B, e -> filterFormatJson()));
        editMenu.add(createMenuItem("menuItemDeepParse", KeyEvent.VK_X, e -> deepParseFormatJson()));
        editMenu.add(createMenuItem("menuItemClose", KeyEvent.VK_W, e -> closeCurrentTab()));
        editMenu.add(createMenuItem("menuItemPaste", KeyEvent.VK_V, e -> Optional.ofNullable(getTextArea()).ifPresent(ta -> {
            ta.paste();
            formatJson();
        })));
        menuBar.add(editMenu);

        // Tool Menu
        JMenu toolMenu = createMenu("toolMenu");
        toolMenu.add(createMenuItem("menuItemLayout", KeyEvent.VK_L, e -> changeLayout()));
        toolMenu.add(createMenuItem("menuItemNew", KeyEvent.VK_N, e -> addTab("NewTab", true)));
        toolMenu.add(createMenuItem("menuItemCode", KeyEvent.VK_T, e -> codeChangeAction()));
        menuBar.add(toolMenu);

        // Help Menu
        JMenu helpMenu = createMenu("helpMenu");
        JMenuItem aboutMenuItem = new JMenuItem(resourceMap.getString("aboutMenu.text"));
        aboutMenuItem.addActionListener(e -> showAboutBox());
        helpMenu.add(aboutMenuItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenu createMenu(String resourceKey) {
        JMenu menu = new JMenu();
        menu.setText(resourceMap.getString(resourceKey + ".text"));
        menu.setName(resourceKey);
        return menu;
    }

    private JMenuItem createMenuItem(String nameKey, int keyCode, ActionListener action) {
        JMenuItem menuItem = new JMenuItem();
        menuItem.setAccelerator(KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_MASK));
        menuItem.setText(resourceMap.getString(nameKey + ".text"));
        menuItem.addActionListener(action);
        return menuItem;
    }

    // --- Tab Management ---

    private void initTabbedContainer() {
        TabData tabData = newTabData("Welcome!", "This is a Tab!", null);
        tabDataModel = new DefaultTabDataModel(new TabData[]{tabData});
        tabbedContainer = new TabbedContainer(tabDataModel, TabbedContainer.TYPE_EDITOR);
        tabbedContainer.setForeground(new Color(238, 238, 238));
        tabbedContainer.getSelectionModel().setSelectedIndex(0);
        tabbedContainer.setShowCloseButton(true);

        tabDataModel.addComplexListDataListener(new ComplexListDataListener() {
            public void indicesAdded(ComplexListDataEvent clde) {
            }

            public void indicesRemoved(ComplexListDataEvent clde) {
            }

            public void indicesChanged(ComplexListDataEvent clde) {
            }

            public void intervalAdded(ListDataEvent e) {
            }

            public void contentsChanged(ListDataEvent e) {
            }

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

        tabbedContainer.addActionListener(e -> {
            if ("select".equalsIgnoreCase(e.getActionCommand())) {
                treePathLst.clear();
            }
        });
    }

    private void closeCurrentTab() {
        int selIndex = getTabIndex();
        if (selIndex >= 0) {
            tabDataModel.removeTab(selIndex);
        }
    }

    private TabData newTabData(String tabName, String tabTip, Icon icon) {
        JSplitPane splitPane = new JSplitPane();
        splitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                splitPane.setDividerLocation(0.45);
            }
        });

        RSyntaxTextArea textArea = newTextArea();
        RTextScrollPane sp = new RTextScrollPane(textArea);
        sp.setFoldIndicatorEnabled(true);
        splitPane.setLeftComponent(sp);

        JSplitPane rightSplitPane = new JSplitPane();
        rightSplitPane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = rightSplitPane.getWidth();
                rightSplitPane.setDividerLocation(w > 500 ? (w - 220) / (float) w : 0.8);
            }
        });

        JTree tree = newTree();
        rightSplitPane.setLeftComponent(new JScrollPane(tree));
        JTable table = newTable();
        rightSplitPane.setRightComponent(new JScrollPane(table));

        splitPane.setRightComponent(rightSplitPane);
        return new TabData(splitPane, icon, tabName, tabTip);
    }

    // --- UI Components Generation ---

    private RSyntaxTextArea newTextArea() {
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
        textArea.addMouseListener(new TextAreaMouseListener());
        return textArea;
    }

    private JTree newTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("o-JSON");
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.addTreeSelectionListener(evt -> treeSelection(tree, getTable()));
        setNodeIcon(tree);
        tree.addMouseListener(new TreeMouseListener(tree));
        return tree;
    }

    private JTable newTable() {
        String[] col = {"key", "value"};
        DefaultTableModel tm = new DefaultTableModel(col, 0);
        JTable table = new JTable(tm);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setAutoscrolls(true);
        table.setMinimumSize(new Dimension(160, 100));
        return table;
    }

    // --- UI Logic: Tree & Table ---

    private void treeSelection(JTree tree, JTable table) {
        DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selNode == null) {
            return;
        }
        DefaultTableModel tm = (DefaultTableModel) table.getModel();
        tm.setColumnCount(2);
        tm.setColumnIdentifiers(new String[]{"key", "value"});

        if (selNode.isLeaf()) {
            tm.setRowCount(1);
            String[] arr = NodeKit.parseTreeNodeUserObject(selNode.toString());
            tm.setValueAt(arr[1], 0, 0);
            tm.setValueAt(arr[2], 0, 1);
        } else {
            int childCount = selNode.getChildCount();
            tm.setRowCount(childCount);
            for (int i = 0; i < childCount; i++) {
                String[] arr = NodeKit.parseTreeNodeUserObject(selNode.getChildAt(i).toString());
                tm.setValueAt(arr[1], i, 0);
                tm.setValueAt(arr[2], i, 1);
            }
        }

        adjustColumnWidths(table);
        table.updateUI();
    }

    private void adjustColumnWidths(JTable table) {
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(getPreferredWidthForColumn(table, column));
        }
    }

    // --- JSON Processing Logic ---

    private void createJsonTree(JsonElement obj, DefaultMutableTreeNode pNode) {
        if (obj.isJsonNull()) {
            pNode.add(NodeKit.nullNode("NULL"));
        } else if (obj.isJsonArray()) {
            createJsonArray(obj.getAsJsonArray(), pNode, "[0]");
        } else if (obj.isJsonObject()) {
            createJsonObject(obj.getAsJsonObject(), pNode);
        } else if (obj.isJsonPrimitive()) {
            formatJsonPrimitive("PRI", obj.getAsJsonPrimitive(), pNode);
        }
    }

    private void createJsonArray(JsonArray arr, DefaultMutableTreeNode pNode, String key) {
        int index = 0;
        DefaultMutableTreeNode child = NodeKit.arrayNode(key);
        for (JsonElement el : arr) {
            String indexKey = NodeKit.formatIndexKey(index);
            if (el.isJsonObject()) {
                DefaultMutableTreeNode node = NodeKit.objectNode(index);
                createJsonObject(el.getAsJsonObject(), node);
                child.add(node);
            } else if (el.isJsonArray()) {
                createJsonArray(el.getAsJsonArray(), child, indexKey);
            } else if (el.isJsonNull()) {
                child.add(NodeKit.nullNode(index));
            } else if (el.isJsonPrimitive()) {
                formatJsonPrimitive(indexKey, el.getAsJsonPrimitive(), child);
            }
            index++;
        }
        pNode.add(child);
    }

    private void createJsonObject(JsonObject obj, DefaultMutableTreeNode pNode) {
        for (Map.Entry<String, JsonElement> el : obj.entrySet()) {
            String key = el.getKey();
            JsonElement val = el.getValue();
            if (val.isJsonNull()) {
                pNode.add(NodeKit.nullNode(key));
            } else if (val.isJsonArray()) {
                createJsonArray(val.getAsJsonArray(), pNode, key);
            } else if (val.isJsonObject()) {
                DefaultMutableTreeNode node = NodeKit.objectNode(key);
                createJsonObject(val.getAsJsonObject(), node);
                pNode.add(node);
            } else if (val.isJsonPrimitive()) {
                formatJsonPrimitive(key, val.getAsJsonPrimitive(), pNode);
            }
        }
    }

    private void formatJsonPrimitive(String key, JsonPrimitive pri, DefaultMutableTreeNode pNode) {
        if (pri.isJsonNull()) {
            pNode.add(NodeKit.nullNode(key));
        } else if (pri.isNumber()) {
            pNode.add(NodeKit.numberNode(key, pri.getAsString()));
        } else if (pri.isBoolean()) {
            pNode.add(NodeKit.booleanNode(key, pri.getAsBoolean()));
        } else if (pri.isString()) {
            pNode.add(NodeKit.stringNode(key, pri.getAsString()));
        }
    }

    private void setNodeIcon(JTree tree) {
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                String tmp = value.toString();
                // 使用缓存的图标，极大提升渲染性能
                if (tmp.startsWith(NodeKit.PREFIX_ARRAY)) {
                    setIcon(iconCache.get("a"));
                } else if (tmp.startsWith(NodeKit.PREFIX_STRING)) {
                    setIcon(iconCache.get("v"));
                } else if (tmp.startsWith(NodeKit.PREFIX_OBJECT)) {
                    setIcon(iconCache.get("o"));
                } else if (tmp.startsWith(NodeKit.PREFIX_NUMBER)) {
                    setIcon(iconCache.get("n"));
                } else if (tmp.startsWith(NodeKit.PREFIX_NULL)) {
                    setIcon(iconCache.get("k"));
                } else if (tmp.startsWith(NodeKit.PREFIX_BOOLEAN)) {
                    setIcon(iconCache.get("v"));
                } else {
                    setIcon(iconCache.get("v"));
                }
                if (tmp.length() > 2) {
                    setText(tmp.substring(2));
                }
                return this;
            }
        });
    }

    // --- Helper Methods ---

    private int addTab(String tabName, boolean isSel) {
        TabData tabData = newTabData(tabName, tabName, null);
        int newIndex = tabbedContainer.getTabCount();
        tabDataModel.addTab(newIndex, tabData);
        if (isSel) {
            tabbedContainer.getSelectionModel().setSelectedIndex(newIndex);
        }
        return newIndex;
    }

    private void modifyText(Consumer<JTextArea> action) {
        Optional.ofNullable(getTextArea()).ifPresent(action);
    }

    private JTextArea getTextArea() {
        return getComponentFromTab(JTextArea.class, 0);
    }

    private JTree getTree(TabData tabData) {
        if (tabData == null) {
            return null;
        }
        return getComponentFromSplitPane(tabData, JTree.class, true);
    }

    private JTree getTree() {
        return getTree(getTabIndex());
    }

    private JTree getTree(int tabIndex) {
        if (tabIndex < 0) {
            return null;
        }
        return getTree(tabDataModel.getTab(tabIndex));
    }

    private JTable getTable() {
        int index = getTabIndex();
        if (index < 0) {
            return null;
        }
        return getComponentFromSplitPane(tabDataModel.getTab(index), JTable.class, false);
    }

    // 通用的组件获取方法，减少重复代码
    @SuppressWarnings("unchecked")
    private <T> T getComponentFromTab(Class<T> clazz, int viewportIndex) {
        int selIndex = getTabIndex();
        if (selIndex >= 0) {
            TabData selTabData = tabDataModel.getTab(selIndex);
            JSplitPane selSplitPane = (JSplitPane) selTabData.getComponent();
            JScrollPane sp = (JScrollPane) selSplitPane.getLeftComponent();
            return (T) sp.getViewport().getView();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private <T> T getComponentFromSplitPane(TabData tabData, Class<T> clazz, boolean isLeftOfRightSplit) {
        JSplitPane selSplitPane = (JSplitPane) tabData.getComponent();
        JSplitPane rightSplitPane = (JSplitPane) selSplitPane.getRightComponent();
        JScrollPane sp = (JScrollPane) (isLeftOfRightSplit ? rightSplitPane.getLeftComponent() : rightSplitPane.getRightComponent());
        return (T) sp.getViewport().getView();
    }

    private int getTabIndex() {
        return tabbedContainer.getSelectionModel().getSelectedIndex();
    }

    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = MainApp.getApplication().getMainFrame();
            aboutBox = new MainAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        MainApp.getApplication().show(aboutBox);
    }

    private void showMessageDialog(String title, String msg) {
        if (msg == null) msg = "";
        String exPrefix = "com.google.gson.stream.MalformedJsonException:";
        if (msg.contains(exPrefix)) {
            msg = msg.substring(msg.indexOf(exPrefix) + exPrefix.length());
        }
        ToolTips tip = new ToolTips();
        tip.setToolTip(title + "\n异常信息：" + msg);
    }

    // --- Table Column Auto-Sizing ---

    private int getPreferredWidthForColumn(JTable table, TableColumn col) {
        int hw = columnHeaderWidth(table, col);
        int cw = widestCellInColumn(table, col);
        return Math.max(hw, cw);
    }

    private int columnHeaderWidth(JTable table, TableColumn col) {
        TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
        Component comp = renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, 0, 0);
        return comp.getPreferredSize().width;
    }

    private int widestCellInColumn(JTable table, TableColumn col) {
        int c = col.getModelIndex();
        int width, maxw = 0;
        for (int r = 0; r < table.getRowCount(); r++) {
            TableCellRenderer renderer = table.getCellRenderer(r, c);
            Component comp = renderer.getTableCellRendererComponent(table, table.getValueAt(r, c), false, false, r, c);
            width = comp.getPreferredSize().width;
            maxw = Math.max(width, maxw);
        }
        return Math.max(maxw, 90) + 10;
    }

    // --- Find / Replace Logic ---

    private void findTreeChildValue(String findText, List<TreePath> treePathLst) {
        JTree tree = getTree();
        if (tree == null) {
            return;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<?> e = root.depthFirstEnumeration();
        treePathLst.clear();
        curPos = 0;

        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.isLeaf()) {
                String str = node.toString();
                if (str.length() > 2 && str.substring(2).contains(findText)) {
                    treePathLst.add(new TreePath(node.getPath()));
                }
            }
        }

        if (!treePathLst.isEmpty()) {
            expandAndSelectPath(tree, treePathLst.get(0));
        }
    }

    private void expandAndSelectPath(JTree tree, TreePath path) {
        tree.expandPath(path);
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    private void showFindDialog(final int type, String title) {
        final JDialog openDlg = new JDialog(getFrame());
        openDlg.setTitle(title);
        openDlg.setModal(false);
        openDlg.setSize(500, 70);
        openDlg.setResizable(false);

        JPanel pane = new JPanel(new FlowLayout(FlowLayout.LEFT));
        openDlg.setContentPane(pane);

        JButton btnFind = new JButton("查找");
        JButton btnNext = new JButton("下一个");
        JButton btnPrev = new JButton("上一个");
        final JTextField textFieldFind = new JTextField(50);

        pane.add(textFieldFind);
        pane.add(btnFind);
        pane.add(btnPrev);
        pane.add(btnNext);

        btnFind.addActionListener(e -> {
            boolean found = false;
            updateDialogTitle(openDlg, false, -1);
            if (type == 1) {
                found = startSegmentFindOrReplaceOperation(getTextArea(), textFieldFind.getText(), true, true, true);
            } else {
                findTreeChildValue(textFieldFind.getText(), treePathLst);
                if (!treePathLst.isEmpty()) found = true;
            }
            updateDialogTitle(openDlg, found, 1);
        });

        btnNext.addActionListener(e -> {
            boolean found = false;
            updateDialogTitle(openDlg, false, -1);
            if (type == 1) {
                found = startSegmentFindOrReplaceOperation(getTextArea(), textFieldFind.getText(), true, true, false);
            } else {
                JTree tree = getTree();
                curPos++;
                if (curPos < treePathLst.size()) {
                    expandAndSelectPath(tree, treePathLst.get(curPos));
                    found = true;
                } else {
                    curPos = treePathLst.size() - 1;
                }
            }
            updateDialogTitle(openDlg, found, 1);
        });

        btnPrev.addActionListener(e -> {
            boolean found = false;
            updateDialogTitle(openDlg, false, -1);
            if (type == 1) {
                found = startSegmentFindOrReplaceOperation(getTextArea(), textFieldFind.getText(), true, false, false);
            } else {
                JTree tree = getTree();
                curPos--;
                if (curPos >= 0) {
                    expandAndSelectPath(tree, treePathLst.get(curPos));
                    found = true;
                } else {
                    curPos = 0;
                }
            }
            updateDialogTitle(openDlg, found, 1);
        });

        openDlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                treePathLst.clear();
                if (type == 1) {
                    isTxtFindDlgOpen = false;
                } else {
                    isTreeFinDlgOpen = false;
                }
            }
        });

        MainApp.getApplication().show(openDlg);
        if (type == 1) {
            isTxtFindDlgOpen = true;
        } else {
            isTreeFinDlgOpen = true;
        }
    }

    private void updateDialogTitle(JDialog dlg, boolean found, int status) {
        String baseTitle = dlg.getTitle().split("-")[0];
        if (status == -1) {
            dlg.setTitle(baseTitle + "-  ==");
        } else {
            dlg.setTitle(baseTitle + (found ? "-  找到了^_^" : "-  没找到╮(╯_╰)╭"));
        }
    }

    public boolean startSegmentFindOrReplaceOperation(JTextArea textArea, String key, boolean ignoreCase, boolean down, boolean isFirst) {
        if (textArea == null || key == null || key.isEmpty()) return false;

        int length = key.length();
        Document doc = textArea.getDocument();
        int offset = textArea.getCaretPosition();
        int docLen = doc.getLength();
        int charsLeft = docLen - offset;

        if (charsLeft <= 0 || isFirst) {
            offset = 0;
            charsLeft = docLen;
        }

        if (!down && !isFirst) {
            offset -= length + 1;
            charsLeft = offset;
        }

        Segment text = new Segment();
        text.setPartialReturn(true);

        try {
            while (charsLeft > 0) {
                doc.getText(offset, length, text);
                String currentText = text.toString();
                boolean match = ignoreCase ? currentText.equalsIgnoreCase(key) : currentText.equals(key);

                if (match) {
                    textArea.requestFocus();
                    textArea.setSelectionStart(offset);
                    textArea.setSelectionEnd(offset + length);
                    return true;
                }

                if (down) {
                    offset++;
                    if (offset + length > docLen) break;
                } else {
                    offset--;
                    if (offset < 0) break;
                }
                charsLeft--;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // --- Actions ---

    private void changeLayout() {
        int selIndex = getTabIndex();
        if (selIndex < 0) {
            return;
        }
        TabData selTabData = tabDataModel.getTab(selIndex);
        JSplitPane splitPane = (JSplitPane) selTabData.getComponent();
        int orient = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT;
        splitPane.setOrientation(orient);
        splitPane.setDividerLocation(0.45);
    }

    private void openFileAction(JTextArea textArea) {
        if (textArea == null) {
            return;
        }
        String title = resourceMap.getString("openDlg.text");
        FileDialog openDlg = new FileDialog(getFrame(), title, FileDialog.LOAD);
        openDlg.setVisible(true);

        if (openDlg.getFile() == null) {
            return;
        }
        File file = new File(openDlg.getDirectory(), openDlg.getFile());

        // 使用 try-with-resources 和 NIO 优化文件读取
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            String content = new String(bytes, Charset.forName(DEFAULT_ENCODING));
            textArea.setText(content);
            formatJson();
        } catch (IOException e) {
            showMessageDialog("读取失败", e.getMessage());
        }
    }

    private void saveFileAction(JTextArea textArea) {
        if (textArea == null) {
            return;
        }
        String title = resourceMap.getString("closeDlg.text");
        FileDialog closeDlg = new FileDialog(getFrame(), title, FileDialog.SAVE);
        closeDlg.setVisible(true);

        if (closeDlg.getFile() == null) {
            return;
        }
        File file = new File(closeDlg.getDirectory(), closeDlg.getFile());

        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), Charset.forName(DEFAULT_ENCODING))) {
            String text = textArea.getText().replace("\n", "\r\n");
            writer.write(text);
        } catch (IOException e) {
            showMessageDialog("保存失败", e.getMessage());
        }
    }

    private void codeChangeAction() {
        JDialog dlg = new JDialog(getFrame(), true);
        dlg.setTitle(resourceMap.getString("menuItemCode.text"));
        dlg.setSize(500, 350);

        JSplitPane spiltPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        spiltPane.setDividerLocation(150);

        final JTextArea textAreaSrc = new JTextArea();
        final JTextArea textAreaDest = new JTextArea();
        textAreaSrc.setLineWrap(true);
        textAreaDest.setLineWrap(true);

        spiltPane.setTopComponent(new JScrollPane(textAreaSrc));
        spiltPane.setBottomComponent(new JScrollPane(textAreaDest));

        JButton btnOK = new JButton("转换");

        dlg.add(spiltPane, BorderLayout.CENTER);
        dlg.add(btnOK, BorderLayout.SOUTH);

        btnOK.addActionListener(e -> {
            try {
                String str = StringEscapeUtils.unescapeJava(textAreaSrc.getText());
                textAreaDest.setText(str);
            } catch (Exception ex) {
                textAreaDest.setText("转换错误: " + ex.getMessage());
            }
        });
        MainApp.getApplication().show(dlg);
    }

    // --- Json Formatting & Tree Building ---

    private void buildTree(JsonElement jsonEle) {
        JTree tree = getTree();
        if (tree == null) {
            return;
        }
        jsonEleTreeMap.put(tree.hashCode(), jsonEle);
        DefaultMutableTreeNode root = NodeKit.objectNode("JSON");
        DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
        try {
            createJsonTree(jsonEle, root);
            model.setRoot(root);
        } catch (Exception ex) {
            root.removeAllChildren();
            model.setRoot(root);
            showMessageDialog("创建json树失败！", ex.getMessage());
        }
    }

    private void processJson(Function<String, Object> jsonProcessor, JSONWriter.Feature... features) {
        JTextArea ta = getTextArea();
        if (ta == null) {
            return;
        }
        String text = ta.getText();
        if (StringUtils.isBlank(text)) {
            return;
        }
        try {
            Object jsonObject = jsonProcessor.apply(text);
            String formattedText = JSON.toJSONString(jsonObject, features);
            JsonElement jsonEle = JsonParser.parseString(formattedText);
            if (jsonEle != null && !jsonEle.isJsonNull()) {
                ta.setText(formattedText);
                buildTree(jsonEle);
            } else {
                showMessageDialog("非法JSON字符串！", "结果为空或格式错误");
            }
        } catch (Exception ex) {
            showMessageDialog("非法JSON字符串！", ex.getMessage());
        }
    }

    private void formatJson() {
        processJson(JSON::parse, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.ReferenceDetection, JSONWriter.Feature.PrettyFormat);
    }

    private void sortFormatJson() {
        processJson(JSON::parse, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.SortMapEntriesByKeys, JSONWriter.Feature.ReferenceDetection, JSONWriter.Feature.PrettyFormat);
    }

    private void zipFormatJson() {
        processJson(JSON::parse, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.ReferenceDetection);
    }

    private void filterFormatJson() {
        processJson(JsonFilter::simpleFilter, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.ReferenceDetection, JSONWriter.Feature.PrettyFormat);
    }

    private void deepParseFormatJson() {
        processJson(ValueParser::parseAndExpand, JSONWriter.Feature.WriteMapNullValue, JSONWriter.Feature.ReferenceDetection, JSONWriter.Feature.PrettyFormat);
    }

    // --- Inner Classes for Listeners (Simplified) ---

    private class TreeMouseListener extends MouseAdapter {
        private final JTree tree;

        public TreeMouseListener(JTree tree) {
            this.tree = tree;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            popupMenu(e);
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            popupMenu(e);
        }

        private void popupMenu(MouseEvent e) {
            if (!e.isPopupTrigger()) return;

            TreePath path = tree.getPathForLocation(e.getX(), e.getY());
            if (path == null) return;

            tree.setSelectionPath(path);
            DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

            JPopupMenu popMenu = new JPopupMenu();
            addTreeMenuItem(popMenu, "复制 键值", 2, selNode);
            addTreeMenuItem(popMenu, "复制 键名", 1, selNode);
            addTreeMenuItem(popMenu, "复制 路径", 4, path);
            addTreeMenuItem(popMenu, "复制 键名键值", 3, selNode);
            addTreeMenuItem(popMenu, "复制 节点内容", 6, path);
            addTreeMenuItem(popMenu, "复制 同路径键值", 5, selNode);
            addTreeMenuItem(popMenu, "复制 MAP式内容", 8, selNode);
            addTreeMenuItem(popMenu, "复制 节点内容带格式", 7, path);

            popMenu.show(e.getComponent(), e.getX(), e.getY());
        }

        private void addTreeMenuItem(JPopupMenu menu, String text, int type, Object obj) {
            JMenuItem item = new JMenuItem(text);
            item.addActionListener(new TreeNodeMenuItemActionListener(tree, type, obj));
            menu.add(item);
        }
    }

    private class TextAreaMouseListener extends MouseAdapter {
        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.isPopupTrigger()) {
                JTextArea ta = getTextArea();
                JPopupMenu popMenu = new JPopupMenu();

                boolean hasSelection = ta != null && ta.getSelectedText() != null && !ta.getSelectedText().isEmpty();

                addMenuItem(popMenu, resourceMap.getString("mtCopy.text"), hasSelection, evt -> Optional.ofNullable(getTextArea()).ifPresent(JTextArea::copy));
                addMenuItem(popMenu, resourceMap.getString("mtPaste.text"), true, evt -> {
                    Optional.ofNullable(getTextArea()).ifPresent(JTextArea::paste);
                    formatJson();
                });
                addMenuItem(popMenu, resourceMap.getString("mtSelAll.text"), true, evt -> Optional.ofNullable(getTextArea()).ifPresent(JTextArea::selectAll));
                addMenuItem(popMenu, resourceMap.getString("mtClean.text"), true, evt -> Optional.ofNullable(getTextArea()).ifPresent(t -> t.setText("")));

                popMenu.show(e.getComponent(), e.getX(), e.getY());
            }
        }

        private void addMenuItem(JPopupMenu menu, String text, boolean enabled, ActionListener action) {
            JMenuItem item = new JMenuItem(text);
            item.setEnabled(enabled);
            item.addActionListener(action);
            menu.add(item);
        }
    }

    // 保留 TreeNodeMenuItemActionListener 因为逻辑较复杂，不适合完全 Lambda 化，但进行了清理
    private class TreeNodeMenuItemActionListener implements ActionListener {
        private final int optType;
        private final Object obj;
        private final JTree tree;

        public TreeNodeMenuItemActionListener(JTree tree, int optType, Object obj) {
            this.optType = optType;
            this.obj = obj;
            this.tree = tree;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (obj == null) return;
            String content = null;

            switch (optType) {
                case 1: // Key
                    content = NodeKit.parseTreeNodeUserObject(obj.toString())[1];
                    break;
                case 2: // Value
                    content = NodeKit.parseTreeNodeUserObject(obj.toString())[2];
                    break;
                case 3: // Key Value
                    content = obj.toString().substring(2);
                    break;
                case 4: // Path
                    String path = copyTreeNodePath((TreePath) obj);
                    content = path.replace(String.valueOf(DOT), ".");
                    break;
                case 5: // Similar Path Values
                    content = copySimilarPathKeyValue((TreeNode) obj);
                    break;
                case 6: // Node Content
                case 7: // Node Content Formatted
                    String p = copyTreeNodePath((TreePath) obj);
                    content = copyNodeContent(p, optType == 7);
                    break;
                case 8: // Map Style
                    String[] arr = NodeKit.parseTreeNodeUserObject(obj.toString());
                    content = "\"" + arr[1] + "\",\"" + arr[2] + "\"";
                    break;
            }

            if (content != null) {
                if ("<null>".equals(content)) content = "null";
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
            }
        }

        private String copyTreeNodePath(TreePath treePath) {
            StringBuilder str = new StringBuilder();
            int len = treePath.getPathCount() - 1;
            for (int i = 0; i <= len; i++) {
                String s = treePath.getPathComponent(i).toString();
                if (i > 0) str.append(DOT);
                if (i == len) str.append(NodeKit.parseTreeNodeUserObject(s)[1]);
                else str.append(s.substring(2));
            }
            // 简单修复格式
            String res = str.toString().replace(DOT + "[", "[");
            return res.length() > 5 ? res.substring(5) : res;
        }

        private String copySimilarPathKeyValue(TreeNode treeNode) {
            StringBuilder str = new StringBuilder();
            String key = NodeKit.parseTreeNodeUserObject(treeNode.toString())[1];
            TreeNode parent = treeNode.getParent();
            if (parent != null && parent.getParent() != null) {
                TreeNode grandParent = parent.getParent();
                int count = grandParent.getChildCount();
                for (int i = 0; i < count; i++) {
                    TreeNode child = grandParent.getChildAt(i);
                    for (int j = 0; j < child.getChildCount(); j++) {
                        TreeNode tmp = child.getChildAt(j);
                        String[] arr = NodeKit.parseTreeNodeUserObject(tmp.toString());
                        if (key != null && key.equals(arr[1])) {
                            str.append(arr[2]).append("\n");
                        }
                    }
                }
            }
            return str.toString();
        }

        private String copyNodeContent(String path, boolean isFormat) {
            String[] arr = StringUtils.split(path, String.valueOf(DOT));
            JsonElement obj = jsonEleTreeMap.get(tree.hashCode());

            if (obj == null) {
                return "";
            }

            try {
                if (arr.length > 1) {
                    for (int i = 1; i < arr.length; i++) {
                        if (obj.isJsonPrimitive()) {
                            break;
                        }
                        String segment = arr[i];
                        int index = NodeKit.getIndex(segment);
                        String key = NodeKit.getKey(segment);

                        if (index == -1) {
                            obj = obj.getAsJsonObject().get(key);
                        } else {
                            obj = obj.getAsJsonObject().getAsJsonArray(key).get(index);
                        }
                    }
                }
                if (obj != null && !obj.isJsonNull()) {
                    GsonBuilder gb = new GsonBuilder().serializeNulls();
                    if (isFormat) {
                        gb.setPrettyPrinting();
                    }
                    return gb.create().toJson(obj);
                }
            } catch (Exception ignored) {
            }
            return "";
        }
    }
}
