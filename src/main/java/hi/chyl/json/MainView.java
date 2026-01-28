package hi.chyl.json;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import hi.chyl.json.service.*;
import hi.chyl.json.utils.NodeKit;
import hi.chyl.json.utils.ToolTips;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.jdesktop.application.Application;
import org.jdesktop.application.FrameView;
import org.jdesktop.application.ResourceMap;
import org.jdesktop.application.SingleFrameApplication;
import org.netbeans.swing.tabcontrol.TabbedContainer;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 主视图类
 * 负责 UI 组装和事件分发，业务逻辑委托给各个 Service
 */
public class MainView extends FrameView {

    // --- Constants ---
    private static final char DOT = 30;

    // --- Services ---
    private final JsonService jsonService = new JsonService();
    private final TreeService treeService = new TreeService();
    private final TabManager tabManager = new TabManager();
    private final SearchService searchService = new SearchService();
    private final FileService fileService = new FileService();

    // --- Resources ---
    private final Map<String, Icon> iconCache = new HashMap<>();
    private final ResourceMap resourceMap;

    // --- Components ---
    private JDialog aboutBox;

    // --- State ---
    private boolean isTxtFindDlgOpen = false;
    private boolean isTreeFinDlgOpen = false;

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
        Icon icon = iconCache.get("json");
        if (icon != null) {
            getFrame().setIconImage(((ImageIcon) icon).getImage());
        }

        setToolBar(createToolBar());
        setMenuBar(createMenuBar());

        TabbedContainer tabbedContainer = tabManager.init();
        tabManager.setTabSelectionListener(v -> searchService.clearTreeSearchResults());

        // 为新创建的标签设置事件监听
        setupCurrentTabListeners();

        setComponent(tabbedContainer);
    }

    // --- Toolbar ---

    private JToolBar createToolBar() {
        JToolBar toolbar = new JToolBar();
        JTextField textField = new JTextField();
        textField.setMaximumSize(new Dimension(180, 100));

        toolbar.add(createToolbarButton("新标签(N)", e -> {
            tabManager.addTab("NewTab", true);
            setupCurrentTabListeners();
        }));
        toolbar.add(createToolbarButton("关闭标签(W)", e -> tabManager.closeCurrentTab()));
        toolbar.add(createToolbarButton("格式化(F)", e -> formatJson()));
        toolbar.add(createToolbarButton("排序(G)", e -> sortFormatJson()));
        toolbar.add(createToolbarButton("压缩(H)", e -> zipFormatJson()));
        toolbar.add(createToolbarButton("去空(B)", e -> filterFormatJson()));
        toolbar.add(createToolbarButton("解析(X)", e -> deepParseFormatJson()));
        toolbar.add(createToolbarButton("清空(D)", e -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(ta -> ta.setText(""))));
        toolbar.add(createToolbarButton("粘帖(V)", e -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(ta -> {
            ta.paste();
            formatJson();
        })));
        toolbar.add(createToolbarButton("清除(\\n)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\n", "")))));
        toolbar.add(createToolbarButton("清除(\\)", e -> modifyText(ta -> ta.setText(ta.getText().replaceAll("\\\\", "")))));
        toolbar.add(createToolbarButton("节点查找", e -> {
            if (!isTreeFinDlgOpen) showFindDialog(2, "树节点查找对话框");
        }));
        toolbar.add(createToolbarButton("文本查找", e -> {
            if (!isTxtFindDlgOpen) showFindDialog(1, "文本查找对话框");
        }));

        toolbar.addSeparator(new Dimension(30, 20));
        toolbar.add(textField);
        JButton btnSelTabName = new JButton("标签名修改");
        btnSelTabName.addActionListener(e -> tabManager.setTabName(tabManager.getSelectedIndex(), textField.getText()));
        toolbar.add(btnSelTabName);
        return toolbar;
    }

    private JButton createToolbarButton(String text, ActionListener action) {
        JButton btn = new JButton(text);
        btn.addActionListener(action);
        return btn;
    }

    // --- Menu Bar ---

    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setName("menuBar");

        // File Menu
        JMenu fileMenu = createMenu("fileMenu");
        fileMenu.add(createMenuItem("menuItemOpenFile", KeyEvent.VK_O, e -> openFileAction()));
        fileMenu.add(createMenuItem("menuItemSaveFile", KeyEvent.VK_S, e -> saveFileAction()));

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
        editMenu.add(createMenuItem("menuItemClose", KeyEvent.VK_W, e -> tabManager.closeCurrentTab()));
        editMenu.add(createMenuItem("menuItemPaste", KeyEvent.VK_V, e -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(ta -> {
            ta.paste();
            formatJson();
        })));
        menuBar.add(editMenu);

        // Tool Menu
        JMenu toolMenu = createMenu("toolMenu");
        toolMenu.add(createMenuItem("menuItemLayout", KeyEvent.VK_L, e -> changeLayout()));
        toolMenu.add(createMenuItem("menuItemNew", KeyEvent.VK_N, e -> {
            tabManager.addTab("NewTab", true);
            setupCurrentTabListeners();
        }));
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

    // --- Setup Tab Listeners ---

    private void setupCurrentTabListeners() {
        JTree tree = tabManager.getTree();
        JTable table = tabManager.getTable();
        JTextArea textArea = tabManager.getTextArea();

        if (tree != null) {
            tree.addTreeSelectionListener(evt -> treeSelection(tree, table));
            setNodeIcon(tree);
            tree.addMouseListener(new TreeMouseListener(tree));
        }

        if (textArea != null) {
            textArea.addMouseListener(new TextAreaMouseListener());
        }
    }

    // --- JSON Processing ---

    private void formatJson() {
        processJsonAction(jsonService::format);
    }

    private void sortFormatJson() {
        processJsonAction(jsonService::formatSorted);
    }

    private void zipFormatJson() {
        processJsonAction(jsonService::compress);
    }

    private void filterFormatJson() {
        processJsonAction(jsonService::filter);
    }

    private void deepParseFormatJson() {
        processJsonAction(jsonService::deepParse);
    }

    private void processJsonAction(java.util.function.Function<String, JsonService.JsonResult> processor) {
        JTextArea ta = tabManager.getTextArea();
        if (ta == null) return;

        String text = ta.getText();
        if (StringUtils.isBlank(text)) return;

        JsonService.JsonResult result = processor.apply(text);
        if (result.isSuccess()) {
            ta.setText(result.getFormattedText());
            buildTree(result.getJsonElement());
        } else {
            showMessageDialog("非法JSON字符串！", result.getErrorMessage());
        }
    }

    private void buildTree(JsonElement jsonEle) {
        JTree tree = tabManager.getTree();
        if (tree == null) return;

        tabManager.putJsonElement(tree, jsonEle);
        try {
            DefaultMutableTreeNode root = treeService.buildTree(jsonEle);
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            model.setRoot(root);
        } catch (Exception ex) {
            DefaultTreeModel model = (DefaultTreeModel) tree.getModel();
            DefaultMutableTreeNode root = NodeKit.objectNode("JSON");
            model.setRoot(root);
            showMessageDialog("创建json树失败！", ex.getMessage());
        }
    }

    // --- Tree & Table ---

    private void treeSelection(JTree tree, JTable table) {
        DefaultMutableTreeNode selNode = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
        if (selNode == null || table == null) return;

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

    private void setNodeIcon(JTree tree) {
        tree.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel,
                                                          boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
                String tmp = value.toString();
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

    // --- File Operations ---

    private void openFileAction() {
        JTextArea textArea = tabManager.getTextArea();
        if (textArea == null) return;

        String title = resourceMap.getString("openDlg.text");
        FileDialog openDlg = new FileDialog(getFrame(), title, FileDialog.LOAD);
        openDlg.setVisible(true);

        if (openDlg.getFile() == null) return;
        File file = new File(openDlg.getDirectory(), openDlg.getFile());

        FileService.FileResult result = fileService.readFile(file);
        if (result.isSuccess()) {
            textArea.setText(result.getContent());
            formatJson();
        } else {
            showMessageDialog("读取失败", result.getErrorMessage());
        }
    }

    private void saveFileAction() {
        JTextArea textArea = tabManager.getTextArea();
        if (textArea == null) return;

        String title = resourceMap.getString("closeDlg.text");
        FileDialog closeDlg = new FileDialog(getFrame(), title, FileDialog.SAVE);
        closeDlg.setVisible(true);

        if (closeDlg.getFile() == null) return;
        File file = new File(closeDlg.getDirectory(), closeDlg.getFile());

        FileService.FileResult result = fileService.saveFile(file, textArea.getText());
        if (!result.isSuccess()) {
            showMessageDialog("保存失败", result.getErrorMessage());
        }
    }

    // --- Find Dialog ---

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
                found = searchService.findInText(tabManager.getTextArea(), textFieldFind.getText(), true, true, true);
            } else {
                found = searchService.findInTree(tabManager.getTree(), textFieldFind.getText());
            }
            updateDialogTitle(openDlg, found, 1);
        });

        btnNext.addActionListener(e -> {
            boolean found = false;
            updateDialogTitle(openDlg, false, -1);
            if (type == 1) {
                found = searchService.findInText(tabManager.getTextArea(), textFieldFind.getText(), true, true, false);
            } else {
                found = searchService.findNextInTree(tabManager.getTree());
            }
            updateDialogTitle(openDlg, found, 1);
        });

        btnPrev.addActionListener(e -> {
            boolean found = false;
            updateDialogTitle(openDlg, false, -1);
            if (type == 1) {
                found = searchService.findInText(tabManager.getTextArea(), textFieldFind.getText(), true, false, false);
            } else {
                found = searchService.findPreviousInTree(tabManager.getTree());
            }
            updateDialogTitle(openDlg, found, 1);
        });

        openDlg.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                searchService.clearTreeSearchResults();
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

    // --- Other Actions ---

    private void changeLayout() {
        JSplitPane splitPane = tabManager.getSplitPane();
        if (splitPane == null) return;

        int orient = splitPane.getOrientation() == JSplitPane.VERTICAL_SPLIT
                ? JSplitPane.HORIZONTAL_SPLIT
                : JSplitPane.VERTICAL_SPLIT;
        splitPane.setOrientation(orient);
        splitPane.setDividerLocation(0.45);
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

    // --- Helper Methods ---

    private void modifyText(java.util.function.Consumer<JTextArea> action) {
        Optional.ofNullable(tabManager.getTextArea()).ifPresent(action);
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

    public void showAboutBox() {
        if (aboutBox == null) {
            JFrame mainFrame = MainApp.getApplication().getMainFrame();
            aboutBox = new MainAboutBox(mainFrame);
            aboutBox.setLocationRelativeTo(mainFrame);
        }
        MainApp.getApplication().show(aboutBox);
    }

    // --- Table Column Sizing ---

    private void adjustColumnWidths(JTable table) {
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(getPreferredWidthForColumn(table, column));
        }
    }

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

    // --- Inner Classes ---

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
                JTextArea ta = tabManager.getTextArea();
                JPopupMenu popMenu = new JPopupMenu();

                boolean hasSelection = ta != null && ta.getSelectedText() != null && !ta.getSelectedText().isEmpty();

                addMenuItem(popMenu, resourceMap.getString("mtCopy.text"), hasSelection,
                        evt -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(JTextArea::copy));
                addMenuItem(popMenu, resourceMap.getString("mtPaste.text"), true, evt -> {
                    Optional.ofNullable(tabManager.getTextArea()).ifPresent(JTextArea::paste);
                    formatJson();
                });
                addMenuItem(popMenu, resourceMap.getString("mtSelAll.text"), true,
                        evt -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(JTextArea::selectAll));
                addMenuItem(popMenu, resourceMap.getString("mtClean.text"), true,
                        evt -> Optional.ofNullable(tabManager.getTextArea()).ifPresent(t -> t.setText("")));

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
                if ("<null>".equals(content)) {
                    content = "null";
                }
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(content), null);
            }
        }

        private String copyTreeNodePath(TreePath treePath) {
            StringBuilder str = new StringBuilder();
            int len = treePath.getPathCount() - 1;
            for (int i = 0; i <= len; i++) {
                String s = treePath.getPathComponent(i).toString();
                if (i > 0) {
                    str.append(DOT);
                }
                if (i == len) {
                    str.append(NodeKit.parseTreeNodeUserObject(s)[1]);
                } else {
                    str.append(s.substring(2));
                }
            }
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
            JsonElement obj = tabManager.getJsonElement(tree);

            if (obj == null) return "";

            try {
                if (arr.length > 1) {
                    for (int i = 1; i < arr.length; i++) {
                        if (obj.isJsonPrimitive()) break;
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
