package hi.chyl.json.model;

/**
 * JSON 树节点数据模型
 * 用对象模型替代原有的字符串编码方式，提高代码可读性和类型安全性
 */
public class TreeNodeData {

    /**
     * 节点类型枚举
     */
    public enum NodeType {
        NULL("k", "<null>"),
        NUMBER("n", null),
        STRING("v", null),
        BOOLEAN("b", null),
        OBJECT("o", "Object"),
        ARRAY("a", "Array");

        private final String code;
        private final String displayValue;

        NodeType(String code, String displayValue) {
            this.code = code;
            this.displayValue = displayValue;
        }

        public String getCode() {
            return code;
        }

        public String getDisplayValue() {
            return displayValue;
        }

        /**
         * 根据类型码获取节点类型
         */
        public static NodeType fromCode(String code) {
            for (NodeType type : values()) {
                if (type.code.equals(code)) {
                    return type;
                }
            }
            return STRING; // 默认为字符串类型
        }
    }

    private static final String KEY_VALUE_SPLITTER = " : ";
    private static final String TYPE_KEY_SIGN = "-";

    private NodeType type;
    private String key;
    private String value;

    public TreeNodeData() {
    }

    public TreeNodeData(NodeType type, String key, String value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    // --- 静态工厂方法 ---

    public static TreeNodeData nullNode(String key) {
        return new TreeNodeData(NodeType.NULL, key, "<null>");
    }

    public static TreeNodeData nullNode(int index) {
        return nullNode(formatIndexKey(index));
    }

    public static TreeNodeData numberNode(String key, String value) {
        return new TreeNodeData(NodeType.NUMBER, key, value);
    }

    public static TreeNodeData numberNode(int index, String value) {
        return numberNode(formatIndexKey(index), value);
    }

    public static TreeNodeData booleanNode(String key, Boolean value) {
        String sVal = value != null && value ? "true" : "false";
        return new TreeNodeData(NodeType.BOOLEAN, key, sVal);
    }

    public static TreeNodeData booleanNode(int index, Boolean value) {
        return booleanNode(formatIndexKey(index), value);
    }

    public static TreeNodeData stringNode(String key, String value) {
        return new TreeNodeData(NodeType.STRING, key, "\"" + value + "\"");
    }

    public static TreeNodeData stringNode(int index, String value) {
        return stringNode(formatIndexKey(index), value);
    }

    public static TreeNodeData objectNode(String key) {
        return new TreeNodeData(NodeType.OBJECT, key, NodeType.OBJECT.getDisplayValue());
    }

    public static TreeNodeData objectNode(int index) {
        return objectNode(formatIndexKey(index));
    }

    public static TreeNodeData arrayNode(String key) {
        return new TreeNodeData(NodeType.ARRAY, key, NodeType.ARRAY.getDisplayValue());
    }

    public static TreeNodeData arrayNode(int index) {
        return arrayNode(formatIndexKey(index));
    }

    /**
     * 格式化数组索引键，如 "[0]", "[1]"
     */
    public static String formatIndexKey(int index) {
        return "[" + index + "]";
    }

    /**
     * 从旧格式字符串解析节点数据（兼容 NodeKit）
     */
    public static TreeNodeData parse(String str) {
        if (str == null || str.length() < 2) {
            return new TreeNodeData();
        }

        String typeCode = str.substring(0, 1);
        NodeType nodeType = NodeType.fromCode(typeCode);
        int valueSplitIndex = str.indexOf(KEY_VALUE_SPLITTER);

        String key;
        String value;

        switch (nodeType) {
            case ARRAY:
            case OBJECT:
                key = str.substring(2);
                value = nodeType.getDisplayValue();
                break;
            default:
                if (valueSplitIndex > 2) {
                    key = str.substring(2, valueSplitIndex);
                    value = str.substring(valueSplitIndex + KEY_VALUE_SPLITTER.length());
                } else {
                    key = str.length() > 2 ? str.substring(2) : "";
                    value = "";
                }
                break;
        }

        return new TreeNodeData(nodeType, key, value);
    }

    /**
     * 转换为旧格式字符串（兼容 NodeKit）
     */
    public String toNodeString() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getCode()).append(TYPE_KEY_SIGN).append(key);

        if (type != NodeType.ARRAY && type != NodeType.OBJECT) {
            sb.append(KEY_VALUE_SPLITTER).append(value);
        }

        return sb.toString();
    }

    /**
     * 获取显示文本（不含类型前缀）
     */
    public String getDisplayText() {
        if (type == NodeType.ARRAY || type == NodeType.OBJECT) {
            return key;
        }
        return key + KEY_VALUE_SPLITTER + value;
    }

    // --- Getter/Setter ---

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return toNodeString();
    }
}
