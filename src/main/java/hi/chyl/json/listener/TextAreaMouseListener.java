package hi.chyl.json.listener;

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Optional;

/**
 * 文本编辑器区域鼠标右键菜单监听器
 */
public class TextAreaMouseListener extends MouseAdapter {

    private final JTextArea textArea;
    private final Runnable formatCallback; // 回调函数：执行格式化
    // 这里简单地传入文本，实际项目中可以通过 ResourceMap 获取
    private static final String COPY_TEXT = "复制";
    private static final String PASTE_TEXT = "粘帖";
    private static final String SEL_ALL_TEXT = "全选";
    private static final String CLEAN_TEXT = "清空";

    public TextAreaMouseListener(JTextArea textArea, Runnable formatCallback) {
        this.textArea = textArea;
        this.formatCallback = formatCallback;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.isPopupTrigger()) {
            JPopupMenu popMenu = new JPopupMenu();
            boolean hasSelection = textArea != null && textArea.getSelectedText() != null && !textArea.getSelectedText().isEmpty();

            addMenuItem(popMenu, COPY_TEXT, hasSelection, evt -> Optional.ofNullable(textArea).ifPresent(JTextArea::copy));
            addMenuItem(popMenu, PASTE_TEXT, true, evt -> {
                Optional.ofNullable(textArea).ifPresent(JTextArea::paste);
                if (formatCallback != null) {
                    formatCallback.run();
                }
            });
            addMenuItem(popMenu, SEL_ALL_TEXT, true, evt -> Optional.ofNullable(textArea).ifPresent(JTextArea::selectAll));
            addMenuItem(popMenu, CLEAN_TEXT, true, evt -> Optional.ofNullable(textArea).ifPresent(t -> t.setText("")));

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
