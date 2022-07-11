package hi.chyl.json;


import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import org.apache.commons.lang.StringUtils;

import java.util.*;

/**
 * 剔除json串中的空map和空数组和空value和空字符串
 *
 * @author lynn
 * @date 2022/7/11 14:16
 * @since v1.0.0
 */
@SuppressWarnings("unchecked")
public final class JsonRuleUtils {

    public static String filter(String json) {
        boolean valid = JSON.isValid(json);
        if (!valid) {
            return json;
        }
        Object parse = JSON.parse(json);
        if (parse instanceof Map) {
            return JSON.toJSONString(filterMap((Map<String, Object>) parse), JSONWriter.Feature.IgnoreNoneSerializable);
        }
        if (parse instanceof List) {
            return JSON.toJSONString(filterList((List<Object>) parse), JSONWriter.Feature.IgnoreNoneSerializable);
        }
        return json;
    }

    private static List<Object> filterList(List<Object> source) {
        List<Object> result = new ArrayList<>();
        for (Object value : source) {
            if (value instanceof Map) {
                Optional.ofNullable(filterMap((Map<String, Object>) value)).ifPresent(result::add);
            } else if (value instanceof List) {
                Optional.ofNullable(filterList((List<Object>) value)).ifPresent(result::add);
            } else {
                if (value != null && StringUtils.isNotEmpty(value.toString())) {
                    result.add(value);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static Map<String, Object> filterMap(Map<String, Object> source) {
        Map<String, Object> result = new HashMap<>(16);
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Map) {
                Optional.ofNullable(filterMap((Map<String, Object>) value)).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
            } else if (value instanceof List) {
                Optional.ofNullable(filterList((List<Object>) value)).ifPresent(filterValue -> result.put(entry.getKey(), filterValue));
            } else {
                if (value != null && StringUtils.isNotEmpty(value.toString())) {
                    result.put(entry.getKey(), value);
                }
            }
        }
        return result.isEmpty() ? null : result;
    }
}
