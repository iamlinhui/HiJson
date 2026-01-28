package hi.chyl.json.service;

import javax.swing.*;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * 查找服务
 * 提供文本查找和树节点查找功能
 */
public class SearchService {

    // 树查找结果缓存
    private final List<TreePath> treePathList = new ArrayList<>();
    private int currentPosition = 0;

    /**
     * 在树中查找包含指定文本的节点
     *
     * @param tree     树组件
     * @param findText 要查找的文本
     * @return 是否找到匹配项
     */
    public boolean findInTree(JTree tree, String findText) {
        if (tree == null || findText == null || findText.isEmpty()) {
            return false;
        }

        DefaultMutableTreeNode root = (DefaultMutableTreeNode) tree.getModel().getRoot();
        Enumeration<?> e = root.depthFirstEnumeration();
        treePathList.clear();
        currentPosition = 0;

        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (node.isLeaf()) {
                String str = node.toString();
                if (str.length() > 2 && str.substring(2).contains(findText)) {
                    treePathList.add(new TreePath(node.getPath()));
                }
            }
        }

        if (!treePathList.isEmpty()) {
            expandAndSelectPath(tree, treePathList.get(0));
            return true;
        }
        return false;
    }

    /**
     * 跳转到树查找结果的下一个匹配项
     *
     * @param tree 树组件
     * @return 是否成功跳转
     */
    public boolean findNextInTree(JTree tree) {
        if (tree == null || treePathList.isEmpty()) {
            return false;
        }

        currentPosition++;
        if (currentPosition < treePathList.size()) {
            expandAndSelectPath(tree, treePathList.get(currentPosition));
            return true;
        } else {
            currentPosition = treePathList.size() - 1;
            return false;
        }
    }

    /**
     * 跳转到树查找结果的上一个匹配项
     *
     * @param tree 树组件
     * @return 是否成功跳转
     */
    public boolean findPreviousInTree(JTree tree) {
        if (tree == null || treePathList.isEmpty()) {
            return false;
        }

        currentPosition--;
        if (currentPosition >= 0) {
            expandAndSelectPath(tree, treePathList.get(currentPosition));
            return true;
        } else {
            currentPosition = 0;
            return false;
        }
    }

    /**
     * 获取树查找结果数量
     */
    public int getTreeSearchResultCount() {
        return treePathList.size();
    }

    /**
     * 获取当前位置
     */
    public int getCurrentPosition() {
        return currentPosition;
    }

    /**
     * 清除树查找结果
     */
    public void clearTreeSearchResults() {
        treePathList.clear();
        currentPosition = 0;
    }

    /**
     * 展开并选中指定的树路径
     */
    private void expandAndSelectPath(JTree tree, TreePath path) {
        tree.expandPath(path);
        tree.setSelectionPath(path);
        tree.scrollPathToVisible(path);
    }

    /**
     * 在文本区域中查找指定文本
     *
     * @param textArea   文本区域
     * @param key        要查找的文本
     * @param ignoreCase 是否忽略大小写
     * @param searchDown 是否向下查找
     * @param isFirst    是否是首次查找
     * @return 是否找到匹配项
     */
    public boolean findInText(JTextArea textArea, String key, boolean ignoreCase,
                              boolean searchDown, boolean isFirst) {
        if (textArea == null || key == null || key.isEmpty()) {
            return false;
        }

        int length = key.length();
        Document doc = textArea.getDocument();
        int offset = textArea.getCaretPosition();
        int docLen = doc.getLength();
        int charsLeft = docLen - offset;

        if (charsLeft <= 0 || isFirst) {
            offset = 0;
            charsLeft = docLen;
        }

        if (!searchDown && !isFirst) {
            offset -= length + 1;
            charsLeft = offset;
        }

        Segment text = new Segment();
        text.setPartialReturn(true);

        try {
            while (charsLeft > 0) {
                doc.getText(offset, length, text);
                String currentText = text.toString();
                boolean match = ignoreCase
                        ? currentText.equalsIgnoreCase(key)
                        : currentText.equals(key);

                if (match) {
                    textArea.requestFocus();
                    textArea.setSelectionStart(offset);
                    textArea.setSelectionEnd(offset + length);
                    return true;
                }

                if (searchDown) {
                    offset++;
                    if (offset + length > docLen) break;
                } else {
                    offset--;
                    if (offset < 0) break;
                }
                charsLeft--;
            }
        } catch (Exception ignored) {
            // 忽略异常
        }
        return false;
    }
}
