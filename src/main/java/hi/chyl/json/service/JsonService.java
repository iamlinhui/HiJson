package hi.chyl.json.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import hi.chyl.json.utils.JsonFilter;
import hi.chyl.json.utils.ValueParser;

import java.util.function.Function;

/**
 * JSON 处理服务
 * 提供 JSON 格式化、排序、压缩、过滤、深度解析等功能
 */
public class JsonService {

    /**
     * JSON 处理结果
     */
    public static class JsonResult {
        private final boolean success;
        private final String formattedText;
        private final JsonElement jsonElement;
        private final String errorMessage;

        private JsonResult(boolean success, String formattedText, JsonElement jsonElement, String errorMessage) {
            this.success = success;
            this.formattedText = formattedText;
            this.jsonElement = jsonElement;
            this.errorMessage = errorMessage;
        }

        public static JsonResult success(String formattedText, JsonElement jsonElement) {
            return new JsonResult(true, formattedText, jsonElement, null);
        }

        public static JsonResult failure(String errorMessage) {
            return new JsonResult(false, null, null, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getFormattedText() {
            return formattedText;
        }

        public JsonElement getJsonElement() {
            return jsonElement;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * 格式化 JSON（带美化缩进）
     */
    public JsonResult format(String jsonText) {
        return processJson(jsonText, JSON::parse,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.ReferenceDetection,
                JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 格式化 JSON 并按键排序
     */
    public JsonResult formatSorted(String jsonText) {
        return processJson(jsonText, JSON::parse,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.SortMapEntriesByKeys,
                JSONWriter.Feature.ReferenceDetection,
                JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 压缩 JSON（移除空格和换行）
     */
    public JsonResult compress(String jsonText) {
        return processJson(jsonText, JSON::parse,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.ReferenceDetection);
    }

    /**
     * 过滤 JSON（移除空值）
     */
    public JsonResult filter(String jsonText) {
        return processJson(jsonText, JsonFilter::simpleFilter,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.ReferenceDetection,
                JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 深度解析 JSON（展开嵌套的 JSON 字符串）
     */
    public JsonResult deepParse(String jsonText) {
        return processJson(jsonText, ValueParser::parseAndExpand,
                JSONWriter.Feature.WriteMapNullValue,
                JSONWriter.Feature.ReferenceDetection,
                JSONWriter.Feature.PrettyFormat);
    }

    /**
     * 核心处理方法
     */
    private JsonResult processJson(String jsonText, Function<String, Object> jsonProcessor,
                                   JSONWriter.Feature... features) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return JsonResult.failure("JSON 文本为空");
        }

        try {
            Object jsonObject = jsonProcessor.apply(jsonText);
            String formattedText = JSON.toJSONString(jsonObject, features);
            JsonElement jsonEle = JsonParser.parseString(formattedText);

            if (jsonEle != null && !jsonEle.isJsonNull()) {
                return JsonResult.success(formattedText, jsonEle);
            } else {
                return JsonResult.failure("结果为空或格式错误");
            }
        } catch (Exception ex) {
            String errorMsg = ex.getMessage();
            if (errorMsg != null) {
                String exPrefix = "com.google.gson.stream.MalformedJsonException:";
                if (errorMsg.contains(exPrefix)) {
                    errorMsg = errorMsg.substring(errorMsg.indexOf(exPrefix) + exPrefix.length());
                }
            }
            return JsonResult.failure(errorMsg);
        }
    }

    /**
     * 验证 JSON 是否有效
     */
    public boolean isValidJson(String jsonText) {
        if (jsonText == null || jsonText.trim().isEmpty()) {
            return false;
        }
        try {
            JSON.parse(jsonText);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
