package hi.chyl.json;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.gson.*;
import hi.chyl.json.listener.TextAreaMouseListener;
import hi.chyl.json.listener.TreeMouseListener;
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
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 主界面视图类
 */
public class MainView extends FrameView {

    // --- Constants ---
    private static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    // 图标资源名称
    private static final String ICON_JSON = "json";
    private static final String ICON_ARRAY = "a";
    private static final String ICON_STRING = "v";
    private static final String ICON_OBJECT = "o";
    private static final String ICON_NUMBER = "n";
    private static final String ICON_NULL = "k";

    // --- Components ---
    private JDialog aboutBox;
    private TabDataModel tabDataModel;
    private TabbedContainer tabbedContainer;

    // 使用 ConcurrentHashMap 增加线程安全性，尽管 Swing 主要在 EDT 运行
    private final Map<Integer, JsonElement> jsonEleTreeMap = new ConcurrentHashMap<>();
    private final Map<String, Icon> iconCache = new HashMap<>();

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
        String[] icons = {ICON_JSON, ICON_ARRAY, ICON_STRING, ICON_OBJECT, ICON_NUMBER, ICON_NULL};
        for (String name : icons) {
            String path = "/images/" + name + (name.equals(ICON_JSON) ? ".png" : ".gif");
            try {
                iconCache.put(name, new ImageIcon(Objects.requireNonNull(getClass().getResource(path))));
            } catch (NullPointerException e) {
                System.err.println("Warning: Missing icon resource " + path);
            }
        }
    }

    private void initUI() {
        Icon icon = iconCache.get(ICON_JSON);
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

        toolbar.add(createBtn("新标签(N)", e -> addTab("NewTab", true)));
        toolbar.add(createBtn("关闭标签(W)", e -> closeCurrentTab()));
        toolbar.add(createBtn("格式化(F)", e -> formatJson()));
        toolbar.add(createBtn("排序(G)", e -> sortFormatJson()));
        toolbar.add(createBtn("压缩(H)", e -> zipFormatJson()));
        toolbar.add(createBtn("去空(B)", e -> filterFormatJson()));
        toolbar.add(createBtn("解析(X)", e -> deepParseFormatJson()));
        toolbar.add(createBtn("清空(D)", e -> modifyText(ta -> ta.setText(""))));
        toolbar.add(createBtn("粘帖(V)", e -> modifyText(ta -> {
            ta.paste();
            formatJson();
        })));
        toolbar.add(createBtn("清除(\\n)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\n", "")))));
        toolbar.add(createBtn("清除(\\)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\\\\", "")))));
        toolbar.add(createBtn("节点查找", e -> showFindDialog(2, "树节点查找对话框")));
        toolbar.add(createBtn("文本查找", e -> showFindDialog(1, "文本查找对话框")));

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

    private JButton createBtn(String text, ActionListener l) {
        JButton btn = new JButton(text);
        btn.addActionListener(l);
        return btn;
    }

    // --- Menu Construction ---

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setName("menuBar");

        JMenu fileMenu = createMenu("fileMenu");
        fileMenu.add(createMenuItem("menuItemOpenFile", KeyEvent.VK_O, e -> openFileAction(getTextArea())));
        fileMenu.add(createMenuItem("menuItemSaveFile", KeyEvent.VK_S, e -> saveFileAction(getTextArea())));

        // Exit Action from AppFramework
        JMenuItem exitMenuItem = new JMenuItem();
        ActionMap actionMap = Application.getInstance(MainApp.class).getContext().getActionMap(MainView.class, this);
        exitMenuItem.setAction(actionMap.get("quit"));
        exitMenuItem.setText(resourceMap.getString("exitMenu.text"));
        fileMenu.add(exitMenuItem);
        menuBar.add(fileMenu);

        JMenu editMenu = createMenu("editMenu");
        editMenu.add(createMenuItem("menuItemClean", KeyEvent.VK_D, e -> modifyText(ta -> ta.setText(""))));
        editMenu.add(createMenuItem("menuItemFormat", KeyEvent.VK_F, e -> formatJson()));
        editMenu.add(createMenuItem("menuItemSortFormat", KeyEvent.VK_G, e -> sortFormatJson()));
        editMenu.add(createMenuItem("menuItemZip", KeyEvent.VK_H, e -> zipFormatJson()));
        editMenu.add(createMenuItem("menuItemFilter", KeyEvent.VK_B, e -> filterFormatJson()));
        editMenu.add(createMenuItem("menuItemDeepParse", KeyEvent.VK_X, e -> deepParseFormatJson()));
        editMenu.add(createMenuItem("menuItemClose", KeyEvent.VK_W, e -> closeCurrentTab()));
        editMenu.add(createMenuItem("menuItemPaste", KeyEvent.VK_V, e -> modifyText(ta -> {
            ta.paste();
            formatJson();
        })));
        menuBar.add(editMenu);

        JMenu toolMenu = createMenu("toolMenu");
        toolMenu.add(createMenuItem("menuItemLayout", KeyEvent.VK_L, e -> changeLayout()));
        toolMenu.add(createMenuItem("menuItemNew", KeyEvent.VK_N, e -> addTab("NewTab", true)));
        toolMenu.add(createMenuItem("menuItemCode", KeyEvent.VK_T, e -> codeChangeAction()));
        menuBar.add(toolMenu);

        JMenu helpMenu = createMenu("helpMenu");
        JMenuItem aboutMenuItem = new JMenuItem(resourceMap.getString("aboutMenu.text"));
        aboutMenuItem.addActionListener(e -> showAboutBox());
        helpMenu.add(aboutMenuItem);
        menuBar.add(helpMenu);

        return menuBar;
    }

    private JMenu createMenu(String key) {
        JMenu menu = new JMenu();
        menu.setText(resourceMap.getString(key + ".text"));
        return menu;
    }

    private JMenuItem createMenuItem(String key, int keyCode, ActionListener l) {
        JMenuItem item = new JMenuItem(resourceMap.getString(key + ".text"));
        item.setAccelerator(KeyStroke.getKeyStroke(keyCode, InputEvent.CTRL_MASK));
        item.addActionListener(l);
        return item;
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

    // --- Component Factories ---

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
        // 使用抽离后的 TextAreaMouseListener
        textArea.addMouseListener(new TextAreaMouseListener(textArea, this::formatJson));
        return textArea;
    }

    private JTree newTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("o-JSON");
        DefaultTreeModel model = new DefaultTreeModel(root);
        JTree tree = new JTree(model);
        tree.addTreeSelectionListener(evt -> treeSelection(tree, getTable()));
        setNodeIcon(tree);
        // 使用抽离后的 TreeMouseListener，并传入 map 引用
        tree.addMouseListener(new TreeMouseListener(tree, jsonEleTreeMap));
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

    // --- UI Logic ---

    private void treeSelection(JTree tree, JTable table) {
        if (tree == null || table == null) return;
        DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selNode == null) return;

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
            int hw = columnHeaderWidth(table, column);
            int cw = widestCellInColumn(table, column);
            column.setPreferredWidth(Math.max(hw, cw));
        }
    }

    private int columnHeaderWidth(JTable table, TableColumn col) {
        TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
        Component comp = renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, 0, 0);
        return comp.getPreferredSize().width;
    }

    private int widestCellInColumn(JTable table, TableColumn col) {
        int c = col.getModelIndex();
        int maxw = 0;
        for (int r = 0; r < table.getRowCount(); r++) {
            TableCellRenderer renderer = table.getCellRenderer(r, c);
            Component comp = renderer.getTableCellRendererComponent(table, table.getValueAt(r, c), false, false, r, c);
            maxw = Math.max(comp.getPreferredSize().width, maxw);
        }
        return Math.max(maxw, 90) + 10;
    }

    // --- JSON Processing Logic ---

    // 递归构建 JTree 节点
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
        DefaultMutableTreeNode child = NodeKit.arrayNode(key);
        int index = 0;
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
                // 根据前缀从缓存获取图标
                Icon icon = iconCache.get(ICON_STRING); // default
                if (tmp.startsWith(NodeKit.PREFIX_ARRAY)) icon = iconCache.get(ICON_ARRAY);
                else if (tmp.startsWith(NodeKit.PREFIX_OBJECT)) icon = iconCache.get(ICON_OBJECT);
                else if (tmp.startsWith(NodeKit.PREFIX_NUMBER)) icon = iconCache.get(ICON_NUMBER);
                else if (tmp.startsWith(NodeKit.PREFIX_NULL)) icon = iconCache.get(ICON_NULL);

                if (icon != null) setIcon(icon);

                if (tmp.length() > 2) {
                    setText(tmp.substring(2));
                }
                return this;
            }
        });
    }

    // --- Core Operations ---

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

    // Helper methods to get components from current tab
    private JTextArea getTextArea() {
        return getComponentFromTab(JTextArea.class);
    }

    private JTree getTree() {
        return getTree(getTabIndex());
    }

    private JTree getTree(TabData tabData) {
        return getComponentFromSplitPane(tabData, JTree.class, true);
    }

    private JTree getTree(int tabIndex) {
        if (tabIndex < 0) return null;
        return getTree(tabDataModel.getTab(tabIndex));
    }

    private JTable getTable() {
        int index = getTabIndex();
        if (index < 0) return null;
        return getComponentFromSplitPane(tabDataModel.getTab(index), JTable.class, false);
    }

    @SuppressWarnings("unchecked")
    private <T> T getComponentFromTab(Class<T> clazz) {
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
        if (tabData == null) return null;
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

    // --- Find / Replace ---

    private void showFindDialog(final int type, String title) {
        if ((type == 1 && isTxtFindDlgOpen) || (type == 2 && isTreeFinDlgOpen)) {
            return;
        }

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

        // Define Action Listener for buttons
        ActionListener listener = e -> {
            boolean found = false;
            String cmd = e.getActionCommand();
            updateDialogTitle(openDlg, false, -1);

            String text = textFieldFind.getText();
            if (type == 1) { // Text Search
                boolean down = !cmd.equals("上一个");
                boolean isFirst = cmd.equals("查找");
                found = startSegmentFindOrReplaceOperation(getTextArea(), text, true, down, isFirst);
            } else { // Tree Search
                if (cmd.equals("查找")) {
                    findTreeChildValue(text, treePathLst);
                    if (!treePathLst.isEmpty()) found = true;
                } else if (cmd.equals("下一个")) {
                    JTree tree = getTree();
                    curPos++;
                    if (curPos < treePathLst.size()) {
                        expandAndSelectPath(tree, treePathLst.get(curPos));
                        found = true;
                    } else curPos = treePathLst.size() - 1;
                } else { // 上一个
                    JTree tree = getTree();
                    curPos--;
                    if (curPos >= 0) {
                        expandAndSelectPath(tree, treePathLst.get(curPos));
                        found = true;
                    } else curPos = 0;
                }
            }
            updateDialogTitle(openDlg, found, 1);
        };

        btnFind.addActionListener(listener);
        btnNext.addActionListener(listener);
        btnPrev.addActionListener(listener);

        openDlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                treePathLst.clear();
                if (type == 1) isTxtFindDlgOpen = false;
                else isTreeFinDlgOpen = false;
            }
        });

        MainApp.getApplication().show(openDlg);
        if (type == 1) isTxtFindDlgOpen = true;
        else isTreeFinDlgOpen = true;
    }

    private void updateDialogTitle(JDialog dlg, boolean found, int status) {
        String baseTitle = dlg.getTitle().split("-")[0];
        if (status == -1) dlg.setTitle(baseTitle + "-  ==");
        else dlg.setTitle(baseTitle + (found ? "-  找到了^_^" : "-  没找到╮(╯_╰)╭"));
    }

    private void findTreeChildValue(String findText, List<TreePath> treePathLst) {
        JTree tree = getTree();
        if (tree == null) return;

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
        if (tree == null) return;
        tree.expandPath(path);
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    public boolean startSegmentFindOrReplaceOperation(JTextArea textArea, String key, boolean ignoreCase, boolean down, boolean isFirst) {
        if (textArea == null || key == null || key.isEmpty()) return false;

        Document doc = textArea.getDocument();
        int length = key.length();
        int offset = textArea.getCaretPosition();
        int docLen = doc.getLength();

        // 逻辑简化：根据方向和是否第一次确定起始位置
        if (isFirst) offset = 0;
        else if (!down) offset -= (length + 1);

        // 边界检查
        if (offset < 0) offset = 0;
        if (offset > docLen) offset = docLen;

        Segment text = new Segment();
        text.setPartialReturn(true);

        try {
            // 简单循环查找，实际可用 Boyer-Moore 或其他算法优化，但在 UI 线程少量文本即可
            while (true) {
                if (down) {
                    if (offset + length > docLen) break;
                } else {
                    if (offset < 0) break;
                }

                doc.getText(offset, length, text);
                String currentText = text.toString();
                boolean match = ignoreCase ? currentText.equalsIgnoreCase(key) : currentText.equals(key);

                if (match) {
                    textArea.requestFocus();
                    textArea.setSelectionStart(offset);
                    textArea.setSelectionEnd(offset + length);
                    return true;
                }

                if (down) offset++;
                else offset--;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    // --- File Actions ---

    private void changeLayout() {
        int selIndex = getTabIndex();
        if (selIndex < 0) return;
        TabData selTabData = tabDataModel.getTab(selIndex);
        JSplitPane splitPane = (JSplitPane) selTabData.getComponent();
        int orient = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT ? JSplitPane.HORIZONTAL_SPLIT : JSplitPane.VERTICAL_SPLIT;
        splitPane.setOrientation(orient);
        splitPane.setDividerLocation(0.45);
    }

    private void openFileAction(JTextArea textArea) {
        if (textArea == null) return;
        FileDialog openDlg = new FileDialog(getFrame(), resourceMap.getString("openDlg.text"), FileDialog.LOAD);
        openDlg.setVisible(true);

        if (openDlg.getFile() != null) {
            File file = new File(openDlg.getDirectory(), openDlg.getFile());
            try {
                byte[] bytes = Files.readAllBytes(file.toPath());
                String content = new String(bytes, DEFAULT_CHARSET);
                textArea.setText(content);
                formatJson();
            } catch (IOException e) {
                showMessageDialog("读取失败", e.getMessage());
            }
        }
    }

    private void saveFileAction(JTextArea textArea) {
        if (textArea == null) return;
        FileDialog closeDlg = new FileDialog(getFrame(), resourceMap.getString("closeDlg.text"), FileDialog.SAVE);
        closeDlg.setVisible(true);

        if (closeDlg.getFile() != null) {
            File file = new File(closeDlg.getDirectory(), closeDlg.getFile());
            try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), DEFAULT_CHARSET)) {
                String text = textArea.getText().replace("\n", "\r\n");
                writer.write(text);
            } catch (IOException e) {
                showMessageDialog("保存失败", e.getMessage());
            }
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

    // --- JSON Logic ---

    private void buildTree(JsonElement jsonEle) {
        JTree tree = getTree();
        if (tree == null) return;

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
        if (ta == null) return;
        String text = ta.getText();
        if (StringUtils.isBlank(text)) return;

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
}
