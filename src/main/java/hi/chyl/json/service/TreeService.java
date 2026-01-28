package hi.chyl.json.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import hi.chyl.json.utils.NodeKit;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.util.Map;

/**
 * JSON 树构建服务
 * 负责将 JsonElement 转换为 JTree 的树节点结构
 */
public class TreeService {

    /**
     * 构建树结构的根节点
     *
     * @param jsonElement JSON 元素
     * @return 树的根节点
     */
    public DefaultMutableTreeNode buildTree(JsonElement jsonElement) {
        DefaultMutableTreeNode root = NodeKit.objectNode("JSON");
        if (jsonElement != null && !jsonElement.isJsonNull()) {
            createJsonTree(jsonElement, root);
        }
        return root;
    }

    /**
     * 更新树模型
     *
     * @param treeModel   树模型
     * @param jsonElement JSON 元素
     */
    public void updateTreeModel(DefaultTreeModel treeModel, JsonElement jsonElement) {
        DefaultMutableTreeNode root = buildTree(jsonElement);
        treeModel.setRoot(root);
    }

    /**
     * 递归创建 JSON 树结构
     */
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

    /**
     * 创建 JSON 数组的树节点
     */
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

    /**
     * 创建 JSON 对象的树节点
     */
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

    /**
     * 处理 JSON 基本类型的树节点
     */
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
}
