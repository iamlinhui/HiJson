package hi.chyl.json.utils;

import com.alibaba.fastjson2.JSON;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON 数据清洗工具类
 * <p>
 * 功能：剔除 JSON 串中的空 Map、空 List、null 值、"null" 字符串以及空字符串。
 * 支持深度清洗（尝试解析字符串中的 JSON 结构）。
 *
 * @author lynn
 * @date 2022/7/11 14:16
 * @since v1.0.0
 */
@SuppressWarnings("unchecked")
public final class JsonFilter {

    private JsonFilter() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * 深度过滤：递归剔除空值，且会尝试将 JSON 格式的字符串解析为对象进行过滤
     *
     * @param json 原始 JSON 字符串
     * @return 过滤后的对象 (Map, List, String 或 null)
     */
    public static Object deepFilter(String json) {
        return parseAndFilter(json, true);
    }

    /**
     * 简单过滤：递归剔除空值，但不解析字符串内部的 JSON 结构
     *
     * @param json 原始 JSON 字符串
     * @return 过滤后的对象 (Map, List, String 或 null)
     */
    public static Object simpleFilter(String json) {
        return parseAndFilter(json, false);
    }

    /**
     * 核心入口：解析 JSON 字符串并开始过滤
     */
    private static Object parseAndFilter(String json, boolean parseString) {
        if (isEmpty(json)) {
            return null;
        }
        try {
            // 使用 Fastjson2 解析，Feature.SupportSmartMatch 可根据需调整
            Object parsedObject = JSON.parse(json);
            return processObject(parsedObject, parseString);
        } catch (Exception e) {
            // 解析失败则视其为普通字符串，进行判空处理
            return isEmpty(json) ? null : json;
        }
    }

    /**
     * 统一递归处理逻辑
     *
     * @param object      当前处理的对象
     * @param parseString 是否尝试解析字符串内容
     * @return 处理后的对象，如果为空则返回 null
     */
    private static Object processObject(Object object, boolean parseString) {
        if (object == null) {
            return null;
        }

        // 1. 处理 Map (JSONObject 本质也是 Map)
        if (object instanceof Map) {
            return processMap((Map<String, Object>) object, parseString);
        }

        // 2. 处理 List (JSONArray 本质也是 List)
        if (object instanceof List) {
            return processList((List<Object>) object, parseString);
        }

        // 3. 处理 String
        if (object instanceof String) {
            return processString((String) object, parseString);
        }

        // 4. 其他基本类型 (Integer, Boolean 等) 直接返回
        return object;
    }

    private static Map<String, Object> processMap(Map<String, Object> source, boolean parseString) {
        if (source.isEmpty()) {
            return null;
        }

        // 使用 LinkedHashMap 保持原始 JSON 的顺序
        Map<String, Object> result = new LinkedHashMap<>(source.size());

        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object filteredValue = processObject(entry.getValue(), parseString);
            if (filteredValue != null) {
                result.put(entry.getKey(), filteredValue);
            }
        }

        return result.isEmpty() ? null : result;
    }

    private static List<Object> processList(List<Object> source, boolean parseString) {
        if (source.isEmpty()) {
            return null;
        }

        List<Object> result = new ArrayList<>(source.size());

        for (Object value : source) {
            Object filteredValue = processObject(value, parseString);
            if (filteredValue != null) {
                result.add(filteredValue);
            }
        }

        return result.isEmpty() ? null : result;
    }

    private static Object processString(String str, boolean parseString) {
        if (isEmpty(str)) {
            return null;
        }

        // 如果不需深度解析，直接返回非空字符串
        if (!parseString) {
            return str;
        }

        // 优化：仅当字符串看起来像 JSON (以 { 或 [ 开头) 时才尝试解析
        // 避免对 "hello world" 这种普通字符串调用 JSON.parse 抛出异常的开销
        str = str.trim();
        if (isJsonStructure(str)) {
            try {
                Object parsed = JSON.parse(str);
                // 递归处理解析出来的对象
                return processObject(parsed, true);
            } catch (Exception e) {
                // 解析失败，说明不是有效的 JSON，按普通非空字符串返回
                return str;
            }
        }

        return str;
    }

    /**
     * 简单判断字符串是否像 JSON 结构
     */
    private static boolean isJsonStructure(String str) {
        if (str == null || str.length() < 2) return false;
        char first = str.charAt(0);
        char last = str.charAt(str.length() - 1);
        return (first == '{' && last == '}') || (first == '[' && last == ']');
    }

    /**
     * 判断字符串是否为空、null 或 "null"
     */
    private static boolean isEmpty(String str) {
        return str == null || str.isEmpty() || "null".equalsIgnoreCase(str);
    }
}
