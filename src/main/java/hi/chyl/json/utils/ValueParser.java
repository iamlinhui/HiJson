package hi.chyl.json.utils;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 字符串值解析工具
 * <p>
 * 场景：某些接口返回的 JSON 中，value 实际上是另一个转义后的 JSON 字符串。
 * 本工具负责将这些字符串“展开”为实际的 JSON 对象或数组。
 *
 * @author lynn
 */
public final class ValueParser {

    private ValueParser() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 解析入口
     *
     * @param json 原始 JSON 字符串
     * @return 解析并展开后的对象
     */
    public static Object parseAndExpand(String json) {
        if (json == null) {
            return null;
        }
        try {
            // 先解析最外层
            Object rawObject = JSON.parse(json);
            // 递归处理内部结构
            return processRecursively(rawObject);
        } catch (Exception e) {
            // 如果最外层都不是标准 JSON，直接返回原字符串
            return json;
        }
    }

    /**
     * 对已有对象进行递归展开
     */
    public static Object parseAndExpand(Object object) {
        return processRecursively(object);
    }

    /**
     * 核心递归逻辑
     */
    @SuppressWarnings("unchecked")
    private static Object processRecursively(Object obj) {
        if (obj == null) {
            return null;
        }

        // 1. 如果是 Map (JSONObject)，遍历处理 value
        if (obj instanceof Map) {
            Map<String, Object> sourceMap = (Map<String, Object>) obj;
            // 使用 LinkedHashMap 保持顺序
            Map<String, Object> resultMap = new LinkedHashMap<>(sourceMap.size());
            for (Map.Entry<String, Object> entry : sourceMap.entrySet()) {
                resultMap.put(entry.getKey(), processRecursively(entry.getValue()));
            }
            return resultMap;
        }

        // 2. 如果是 List (JSONArray)，遍历处理元素
        if (obj instanceof List) {
            List<Object> sourceList = (List<Object>) obj;
            List<Object> resultList = new ArrayList<>(sourceList.size());
            for (Object item : sourceList) {
                resultList.add(processRecursively(item));
            }
            return resultList;
        }

        // 3. 如果是 String，尝试检测是否为 JSON 并解析
        if (obj instanceof String) {
            String strVal = (String) obj;
            // 预判：只有看起来像 JSON 的字符串才尝试解析，提升性能
            if (isLikeJson(strVal)) {
                try {
                    Object parsedValue = JSON.parse(strVal);
                    // 解析成功后，递归调用，防止“套娃” (JSON string inside JSON string)
                    return processRecursively(parsedValue);
                } catch (Exception e) {
                    // 解析失败（可能只是长得像 JSON 的普通文本），忽略异常，返回原字符串
                    return strVal;
                }
            }
            return strVal;
        }

        // 4. 其他类型（Number, Boolean 等）原样返回
        return obj;
    }

    /**
     * 简单判断字符串是否像 JSON (以 { 或 [ 开头，以 } 或 ] 结尾)
     */
    private static boolean isLikeJson(String str) {
        if (str == null || str.length() < 2) {
            return false;
        }
        String trimmed = str.trim();
        if (trimmed.length() < 2) return false;

        char first = trimmed.charAt(0);
        char last = trimmed.charAt(trimmed.length() - 1);

        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }
}
