package hi.chyl.json;

import com.alibaba.fastjson2.JSON;

import java.util.*;

/**
 * 剔除json串中的空map和空数组和空value和空字符串, 并解析String的json串
 *
 * @author lynn
 * @date 2022/7/11 14:16
 * @since v1.0.0
 */
@SuppressWarnings("unchecked")
public final class JsonUtils {

    public static Object filterString(String json) {

        boolean valid = JSON.isValidObject(json) || JSON.isValidArray(json);
        if (!valid) {
            return isEmpty(json) ? null : json;
        }
        Object parse = JSON.parse(json);
        if (parse instanceof Map) {
            return filterMap((Map<String, Object>) parse);
        }
        if (parse instanceof List) {
            return filterList((List<Object>) parse);
        }
        return parse;
    }

    private static List<Object> filterList(List<Object> source) {
        List<Object> result = new ArrayList<>();
        for (Object value : source) {
            if (value instanceof Map) {
                Optional.ofNullable(filterMap((Map<String, Object>) value)).ifPresent(result::add);
                continue;
            }
            if (value instanceof List) {
                Optional.ofNullable(filterList((List<Object>) value)).ifPresent(result::add);
                continue;
            }
            if (value instanceof String) {
                Optional.ofNullable(filterString((String) value)).ifPresent(result::add);
                continue;
            }
            Optional.ofNullable(value).ifPresent(result::add);
        }
        return result.isEmpty() ? null : result;
    }

    private static Map<String, Object> filterMap(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Optional.ofNullable(filterMap((Map<String, Object>) value)).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
                continue;
            }
            if (value instanceof List) {
                Optional.ofNullable(filterList((List<Object>) value)).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
                continue;
            }
            if (value instanceof String) {
                Optional.ofNullable(filterString((String) value)).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
                continue;
            }
            Optional.ofNullable(value).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
        }
        return result.isEmpty() ? null : result;
    }

    private static boolean isEmpty(Object str) {
        return (str == null || "".equals(str) || "null".equals(str));
    }
}
