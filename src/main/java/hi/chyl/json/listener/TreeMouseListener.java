package hi.chyl.json.listener;

import com.google.gson.JsonElement;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;

/**
 * JTree 鼠标右键菜单监听器
 */
public class TreeMouseListener extends MouseAdapter {
    private final JTree tree;
    private final Map<Integer, JsonElement> jsonEleTreeMap;

    public TreeMouseListener(JTree tree, Map<Integer, JsonElement> jsonEleTreeMap) {
        this.tree = tree;
        this.jsonEleTreeMap = jsonEleTreeMap;
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
        addTreeMenuItem(popMenu, "复制 键值", TreeNodeMenuItemActionListener.COPY_VALUE, selNode);
        addTreeMenuItem(popMenu, "复制 键名", TreeNodeMenuItemActionListener.COPY_KEY, selNode);
        addTreeMenuItem(popMenu, "复制 路径", TreeNodeMenuItemActionListener.COPY_PATH, path);
        addTreeMenuItem(popMenu, "复制 键名键值", TreeNodeMenuItemActionListener.COPY_KEY_VALUE, selNode);
        addTreeMenuItem(popMenu, "复制 节点内容", TreeNodeMenuItemActionListener.COPY_NODE_CONTENT, path);
        addTreeMenuItem(popMenu, "复制 同路径键值", TreeNodeMenuItemActionListener.COPY_SIMILAR_PATH_VALUES, selNode);
        addTreeMenuItem(popMenu, "复制 MAP式内容", TreeNodeMenuItemActionListener.COPY_MAP_STYLE, selNode);
        addTreeMenuItem(popMenu, "复制 节点内容带格式", TreeNodeMenuItemActionListener.COPY_NODE_CONTENT_FORMATTED, path);

        popMenu.show(e.getComponent(), e.getX(), e.getY());
    }

    private void addTreeMenuItem(JPopupMenu menu, String text, int type, Object obj) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(new TreeNodeMenuItemActionListener(tree, jsonEleTreeMap, type, obj));
        menu.add(item);
    }
}
