package com.hczk.hczkaiagentserver.knowledge.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Map;

/**
 * Milvus SDK 数据转换工具
 * Milvus SDK v2 的 InsertReq/UpsertReq 需要 JsonObject (Gson)，
 * 而 QueryResp 返回 Map<String, Object>，需要互相转换
 */
public class MilvusDataConverter {

    private static final Gson GSON = new Gson();

    /**
     * 将 Map<String, Object> 转换为 JsonObject
     * 处理 float[] 向量字段的特殊转换
     */
    public static JsonObject toJsonObject(Map<String, Object> map) {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            addProperty(obj, entry.getKey(), value);
        }
        return obj;
    }

    /**
     * 将 Map 列表转换为 JsonObject 列表
     */
    public static List<JsonObject> toJsonObjectList(List<Map<String, Object>> maps) {
        return maps.stream().map(MilvusDataConverter::toJsonObject).toList();
    }

    private static void addProperty(JsonObject obj, String key, Object value) {
        if (value instanceof String s) {
            obj.addProperty(key, s);
        } else if (value instanceof Number n) {
            obj.addProperty(key, n);
        } else if (value instanceof Boolean b) {
            obj.addProperty(key, b);
        } else if (value instanceof float[] arr) {
            JsonArray jsonArray = new JsonArray(arr.length);
            for (float v : arr) {
                jsonArray.add(v);
            }
            obj.add(key, jsonArray);
        } else if (value instanceof double[] arr) {
            JsonArray jsonArray = new JsonArray(arr.length);
            for (double v : arr) {
                jsonArray.add(v);
            }
            obj.add(key, jsonArray);
        } else if (value instanceof int[] arr) {
            JsonArray jsonArray = new JsonArray(arr.length);
            for (int v : arr) {
                jsonArray.add(v);
            }
            obj.add(key, jsonArray);
        } else if (value instanceof long[] arr) {
            JsonArray jsonArray = new JsonArray(arr.length);
            for (long v : arr) {
                jsonArray.add(v);
            }
            obj.add(key, jsonArray);
        } else if (value instanceof List<?> list) {
            JsonArray jsonArray = new JsonArray();
            for (Object item : list) {
                if (item instanceof Number n) {
                    jsonArray.add(n);
                } else if (item instanceof String s) {
                    jsonArray.add(s);
                } else if (item instanceof Boolean b) {
                    jsonArray.add(b);
                } else {
                    jsonArray.add(GSON.toJsonTree(item));
                }
            }
            obj.add(key, jsonArray);
        } else if (value instanceof Map<?, ?> map) {
            obj.add(key, GSON.toJsonTree(map));
        } else {
            obj.add(key, GSON.toJsonTree(value));
        }
    }
}
