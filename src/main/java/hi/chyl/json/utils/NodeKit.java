package hi.chyl.json.utils;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * JSON 数据树形结构工具包。
 * 用于构建和解析 {@link DefaultMutableTreeNode} 的用户对象字符串，
 * 字符串中编码了 JSON 值的类型、键名/索引和值。
 */
public final class NodeKit {

    // --- 字符串分隔符和标记 ---
    /**
     * 键(Key) 和 值(Value) 之间的分隔符
     */
    public static final String KEY_VALUE_SPLITTER = " : ";
    /**
     * 类型码 和 键名/索引 之间的分隔符
     */
    public static final String TYPE_KEY_SIGN = "-";

    // --- 单字符类型编码 (Type Codes) ---
    public static final String CODE_NULL = "k";  // null
    public static final String CODE_NUMBER = "n";  // Number
    public static final String CODE_OBJECT = "o";  // JSON Object
    public static final String CODE_ARRAY = "a";   // JSON Array
    public static final String CODE_STRING = "v";  // String (Value)
    public static final String CODE_BOOLEAN = "b"; // Boolean

    // --- 完整类型前缀 (Type Prefixes for User Object) ---
    public static final String PREFIX_NULL = CODE_NULL + TYPE_KEY_SIGN;
    public static final String PREFIX_NUMBER = CODE_NUMBER + TYPE_KEY_SIGN;
    public static final String PREFIX_OBJECT = CODE_OBJECT + TYPE_KEY_SIGN;
    public static final String PREFIX_ARRAY = CODE_ARRAY + TYPE_KEY_SIGN;
    public static final String PREFIX_STRING = CODE_STRING + TYPE_KEY_SIGN;
    public static final String PREFIX_BOOLEAN = CODE_BOOLEAN + TYPE_KEY_SIGN;

    // --- 集合类型显示值 ---
    public static final String DISPLAY_ARRAY = "Array";
    public static final String DISPLAY_OBJECT = "Object";


    // --- 节点创建方法 ---

    public static DefaultMutableTreeNode nullNode(String key) {
        return createTreeNode(PREFIX_NULL + key + KEY_VALUE_SPLITTER + "<null>");
    }

    public static DefaultMutableTreeNode nullNode(int index) {
        return nullNode(formatIndexKey(index));
    }

    public static DefaultMutableTreeNode numberNode(String key, String val) {
        return createTreeNode(PREFIX_NUMBER + key + KEY_VALUE_SPLITTER + val);
    }

    public static DefaultMutableTreeNode numberNode(int index, String val) {
        return numberNode(formatIndexKey(index), val);
    }

    public static DefaultMutableTreeNode booleanNode(String key, Boolean val) {
        String sVal = val != null && val ? "true" : "false";
        return createTreeNode(PREFIX_BOOLEAN + key + KEY_VALUE_SPLITTER + sVal);
    }

    public static DefaultMutableTreeNode booleanNode(int index, Boolean val) {
        return booleanNode(formatIndexKey(index), val);
    }

    public static DefaultMutableTreeNode stringNode(String key, String val) {
        // 字符串值通常用双引号包裹显示
        return createTreeNode(PREFIX_STRING + key + KEY_VALUE_SPLITTER + "\"" + val + "\"");
    }

    public static DefaultMutableTreeNode stringNode(int index, String val) {
        return stringNode(formatIndexKey(index), val);
    }

    public static DefaultMutableTreeNode objectNode(String key) {
        return createTreeNode(PREFIX_OBJECT + key);
    }

    public static DefaultMutableTreeNode objectNode(int index) {
        return objectNode(formatIndexKey(index));
    }

    public static DefaultMutableTreeNode arrayNode(String key) {
        return createTreeNode(PREFIX_ARRAY + key);
    }

    public static DefaultMutableTreeNode arrayNode(int index) {
        return arrayNode(formatIndexKey(index));
    }

    // --- 核心工具方法 ---

    /**
     * 创建一个包含用户对象字符串的 {@link DefaultMutableTreeNode}。
     *
     * @param str 节点的显示字符串（用户对象）。
     * @return 新的树节点。
     */
    public static DefaultMutableTreeNode createTreeNode(String str) {
        return new DefaultMutableTreeNode(str);
    }

    /**
     * 格式化数组元素的索引键，例如 "[0]", "[1]"。
     *
     * @param index 数组索引。
     * @return 格式化后的键字符串。
     */
    public static String formatIndexKey(int index) {
        return "[" + index + "]";
    }

    /**
     * 格式化数组节点的键，例如 "a-[0]"。
     *
     * @param index 数组索引。
     * @return 格式化后的键字符串。
     */
    public static String formatArrayNodeKey(int index) {
        return PREFIX_ARRAY + formatIndexKey(index);
    }


    // --- 节点解析方法 ---

    /**
     * 从节点的用户对象字符串中提取数组索引。
     *
     * @param str 节点字符串，例如 "a-[10]" 或 "o-key[10]".
     * @return 提取到的索引，如果不存在或解析失败则返回 -1。
     */
    public static int getIndex(String str) {
        if (str == null || str.isEmpty()) {
            return -1;
        }
        int startIndex = str.lastIndexOf("[");
        if (startIndex >= 0) {
            try {
                // 提取 "[" 和 "]" 之间的内容
                String indexStr = str.substring(startIndex + 1, str.length() - 1);
                return Integer.parseInt(indexStr);
            } catch (Exception ex) {
                // NumberFormatException 或 IndexOutOfBoundsException
                return -1;
            }
        }
        return -1;
    }

    /**
     * 从节点的用户对象字符串中提取键名 (Key)，移除索引部分。
     *
     * @param str 节点字符串，例如 "a-key[10]" 或 "o-key".
     * @return 移除索引后的键名，如果无索引则返回原字符串。
     */
    public static String getKey(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        int index = str.lastIndexOf("[");
        if (index >= 0) {
            // 返回索引 "[" 之前的部分
            return str.substring(0, index);
        }
        return str;
    }

    /**
     * 解析节点的用户对象字符串，提取类型、键和值。
     *
     * @param str 节点的完整用户对象字符串。
     * @return 包含 [类型码, 键名, 值] 的字符串数组。
     */
    public static String[] parseTreeNodeUserObject(String str) {
        String[] arr = new String[3]; // [类型, 键, 值]

        if (str == null || str.length() < 2) {
            // 如果字符串太短，无法包含类型码和分隔符，返回空数组
            return new String[3];
        }

        // 1. 提取类型码 (第一个字符)
        arr[0] = str.substring(0, 1);

        // 查找键值分隔符 ": " 的位置
        int valueSplitIndex = str.indexOf(KEY_VALUE_SPLITTER);

        switch (arr[0]) {
            case CODE_ARRAY:
                // 数组节点: 格式 "a-key"
                arr[1] = str.substring(2); // 跳过 "a-"
                arr[2] = DISPLAY_ARRAY;
                break;
            case CODE_OBJECT:
                // 对象节点: 格式 "o-key"
                arr[1] = str.substring(2); // 跳过 "o-"
                arr[2] = DISPLAY_OBJECT;
                break;
            case CODE_STRING:
                // 字符串节点: 格式 "v-key : "val""
                if (valueSplitIndex > 2) {
                    arr[1] = str.substring(2, valueSplitIndex);
                    // 值是 valueSplitIndex 之后的部分，包括引号
                    arr[2] = str.substring(valueSplitIndex + KEY_VALUE_SPLITTER.length());
                }
                break;
            default:
                // 其他值类型 (null, number, boolean): 格式 "t-key : value"
                if (valueSplitIndex > 2) {
                    arr[1] = str.substring(2, valueSplitIndex);
                    arr[2] = str.substring(valueSplitIndex + KEY_VALUE_SPLITTER.length());
                }
                break;
        }
        return arr;
    }
}
