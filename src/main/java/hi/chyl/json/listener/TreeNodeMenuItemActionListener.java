package hi.chyl.json.listener;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import hi.chyl.json.utils.NodeKit;
import org.apache.commons.lang.StringUtils;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Map;

/**
 * 树节点右键菜单动作监听器
 */
public class TreeNodeMenuItemActionListener implements ActionListener {

    // 操作类型常量
    public static final int COPY_KEY = 1;
    public static final int COPY_VALUE = 2;
    public static final int COPY_KEY_VALUE = 3;
    public static final int COPY_PATH = 4;
    public static final int COPY_SIMILAR_PATH_VALUES = 5;
    public static final int COPY_NODE_CONTENT = 6;
    public static final int COPY_NODE_CONTENT_FORMATTED = 7;
    public static final int COPY_MAP_STYLE = 8;

    private static final char DOT = 30; // 内部路径分隔符

    private final int optType;
    private final Object targetObj;
    private final JTree tree;
    // 需要访问 MainView 中的 jsonMap 来获取原始 JsonElement
    private final Map<Integer, JsonElement> jsonEleTreeMap;

    public TreeNodeMenuItemActionListener(JTree tree, Map<Integer, JsonElement> jsonEleTreeMap, int optType, Object targetObj) {
        this.tree = tree;
        this.jsonEleTreeMap = jsonEleTreeMap;
        this.optType = optType;
        this.targetObj = targetObj;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (targetObj == null) return;
        String content = null;

        switch (optType) {
            case COPY_KEY:
                content = NodeKit.parseTreeNodeUserObject(targetObj.toString())[1];
                break;
            case COPY_VALUE:
                content = NodeKit.parseTreeNodeUserObject(targetObj.toString())[2];
                break;
            case COPY_KEY_VALUE:
                content = targetObj.toString().substring(2);
                break;
            case COPY_PATH:
                String path = copyTreeNodePath((TreePath) targetObj);
                content = path.replace(String.valueOf(DOT), ".");
                break;
            case COPY_SIMILAR_PATH_VALUES:
                content = copySimilarPathKeyValue((TreeNode) targetObj);
                break;
            case COPY_NODE_CONTENT:
            case COPY_NODE_CONTENT_FORMATTED:
                String p = copyTreeNodePath((TreePath) targetObj);
                content = copyNodeContent(p, optType == COPY_NODE_CONTENT_FORMATTED);
                break;
            case COPY_MAP_STYLE:
                String[] arr = NodeKit.parseTreeNodeUserObject(targetObj.toString());
                content = "\"" + arr[1] + "\",\"" + arr[2] + "\"";
                break;
            default:
                break;
        }

        if (content != null) {
            if ("<null>".equals(content)) content = "null";
            setClipboardString(content);
        }
    }

    private void setClipboardString(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
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
                        if (obj.isJsonObject()) {
                            obj = obj.getAsJsonObject().get(key);
                        }
                    } else {
                        if (obj.isJsonObject() && obj.getAsJsonObject().has(key)) {
                            JsonElement arrEle = obj.getAsJsonObject().get(key);
                            if (arrEle.isJsonArray()) {
                                obj = arrEle.getAsJsonArray().get(index);
                            }
                        }
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
            // Log exception if needed
        }
        return "";
    }
}
