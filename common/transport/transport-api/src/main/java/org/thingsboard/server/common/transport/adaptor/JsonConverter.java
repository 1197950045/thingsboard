/**
 * Copyright © 2016-2019 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.common.transport.adaptor;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.util.StringUtils;
import org.thingsboard.server.common.data.DataConstants;
import org.thingsboard.server.common.data.id.DeviceId;
import org.thingsboard.server.common.data.kv.AttributeKey;
import org.thingsboard.server.common.data.kv.AttributeKvEntry;
import org.thingsboard.server.common.data.kv.BaseAttributeKvEntry;
import org.thingsboard.server.common.data.kv.BooleanDataEntry;
import org.thingsboard.server.common.data.kv.DoubleDataEntry;
import org.thingsboard.server.common.data.kv.KvEntry;
import org.thingsboard.server.common.data.kv.LongDataEntry;
import org.thingsboard.server.common.data.kv.StringDataEntry;
import org.thingsboard.server.common.msg.kv.AttributesKVMsg;
import org.thingsboard.server.gen.transport.TransportProtos;
import org.thingsboard.server.gen.transport.TransportProtos.AttributeUpdateNotificationMsg;
import org.thingsboard.server.gen.transport.TransportProtos.ClaimDeviceMsg;
import org.thingsboard.server.gen.transport.TransportProtos.GetAttributeResponseMsg;
import org.thingsboard.server.gen.transport.TransportProtos.KeyValueProto;
import org.thingsboard.server.gen.transport.TransportProtos.KeyValueType;
import org.thingsboard.server.gen.transport.TransportProtos.PostAttributeMsg;
import org.thingsboard.server.gen.transport.TransportProtos.PostTelemetryMsg;
import org.thingsboard.server.gen.transport.TransportProtos.TsKvListProto;
import org.thingsboard.server.gen.transport.TransportProtos.TsKvProto;

import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class JsonConverter {

    private static final Gson GSON = new Gson();
    private static final String CAN_T_PARSE_VALUE = "Can't parse value: ";
    private static final String DEVICE_PROPERTY = "device";

    private static boolean isTypeCastEnabled = true;

    private static int maxStringValueLength = 0;

    public static PostTelemetryMsg convertToTelemetryProto(JsonElement jsonElement) throws JsonSyntaxException {
        PostTelemetryMsg.Builder builder = PostTelemetryMsg.newBuilder();
        convertToTelemetry(jsonElement, System.currentTimeMillis(), null, builder);
        return builder.build();
    }

    public static PostTelemetryMsg convertToFyTelemetryProto(JSONObject jsonElement) throws JsonSyntaxException {
        PostTelemetryMsg.Builder builder = PostTelemetryMsg.newBuilder();
        convertToFyTelemetry(jsonElement, System.currentTimeMillis(), null, builder);
        return builder.build();
    }

    public static PostTelemetryMsg convertToDevinfoProto(JsonElement jsonElement) throws JsonSyntaxException {
        PostTelemetryMsg.Builder builder = PostTelemetryMsg.newBuilder();
        convertToDevinfo(jsonElement, System.currentTimeMillis(), null, builder);
        return builder.build();
    }

    private static void convertToTelemetry(JsonElement jsonElement, long systemTs, Map<Long, List<KvEntry>> result, PostTelemetryMsg.Builder builder) {
        if (jsonElement.isJsonObject()) {
            parseObject(systemTs, result, builder, jsonElement.getAsJsonObject());
        } else if (jsonElement.isJsonArray()) {
            jsonElement.getAsJsonArray().forEach(je -> {
                if (je.isJsonObject()) {
                    parseObject(systemTs, result, builder, je.getAsJsonObject());
                } else {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + je);
                }
            });
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + jsonElement);
        }
    }

    private static void convertToFyTelemetry(JSONObject jo, long systemTs, Map<Long, List<KvEntry>> result, PostTelemetryMsg.Builder builder) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            long ts = simpleDateFormat.parse(jo.getString("time"), new ParsePosition(0)).getTime();
            JSONArray ja = jo.getJSONArray("Data");
            JsonObject values = new JsonObject();
            for (int i = 0; i < ja.size(); i++) {
                values.addProperty(((JSONObject)ja.get(i)).getString("name"),((JSONObject)ja.get(i)).getString("value"));
            }
            JsonObject data = new JsonObject();
            data.addProperty("ts",ts);
            data.add("values",values);
            parseObject(systemTs, result, builder, data);

    }

//   coap数据具体处理方法
    private static void convertToDevinfo(JsonElement jsonElement, long systemTs, Map<Long, List<KvEntry>> result, PostTelemetryMsg.Builder builder) {
        if (jsonElement.isJsonObject()) {
            devinfoObject(systemTs, result, builder, jsonElement.getAsJsonObject());
        } else if (jsonElement.isJsonArray()) {
            jsonElement.getAsJsonArray().forEach(je -> {
                if (je.isJsonObject()) {
                    devinfoObject(systemTs, result, builder, je.getAsJsonObject());
                } else {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + je);
                }
            });
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + jsonElement);
        }
    }

    private static void parseObject(long systemTs, Map<Long, List<KvEntry>> result, PostTelemetryMsg.Builder builder, JsonObject jo) {
        if (result != null) {
            parseObject(result, systemTs, jo);
        } else {
            parseObject(builder, systemTs, jo);
        }
    }

    private static void devinfoObject(long systemTs, Map<Long, List<KvEntry>> result, PostTelemetryMsg.Builder builder, JsonObject jo)  {
        if (result != null) {
            devinfoObject(result, systemTs, jo);
        } else {
            devinfoObject(builder, systemTs, jo);
        }
    }

    public static ClaimDeviceMsg convertToClaimDeviceProto(DeviceId deviceId, String json) {
        long durationMs = 0L;
        if (json != null && !json.isEmpty()) {
            return convertToClaimDeviceProto(deviceId, new JsonParser().parse(json));
        }
        return buildClaimDeviceMsg(deviceId, DataConstants.DEFAULT_SECRET_KEY, durationMs);
    }

    public static ClaimDeviceMsg convertToClaimDeviceProto(DeviceId deviceId, JsonElement jsonElement) {
        String secretKey = DataConstants.DEFAULT_SECRET_KEY;
        long durationMs = 0L;
        if (jsonElement.isJsonObject()) {
            JsonObject jo = jsonElement.getAsJsonObject();
            if (jo.has(DataConstants.SECRET_KEY_FIELD_NAME)) {
                secretKey = jo.get(DataConstants.SECRET_KEY_FIELD_NAME).getAsString();
            }
            if (jo.has(DataConstants.DURATION_MS_FIELD_NAME)) {
                durationMs = jo.get(DataConstants.DURATION_MS_FIELD_NAME).getAsLong();
            }
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + jsonElement);
        }
        return buildClaimDeviceMsg(deviceId, secretKey, durationMs);
    }

    private static ClaimDeviceMsg buildClaimDeviceMsg(DeviceId deviceId, String secretKey, long durationMs) {
        ClaimDeviceMsg.Builder result = ClaimDeviceMsg.newBuilder();
        return result
                .setDeviceIdMSB(deviceId.getId().getMostSignificantBits())
                .setDeviceIdLSB(deviceId.getId().getLeastSignificantBits())
                .setSecretKey(secretKey)
                .setDurationMs(durationMs)
                .build();
    }

    public static PostAttributeMsg convertToAttributesProto(JsonElement jsonObject) throws JsonSyntaxException {
        if (jsonObject.isJsonObject()) {
            PostAttributeMsg.Builder result = PostAttributeMsg.newBuilder();
            List<KeyValueProto> keyValueList = parseProtoValues(jsonObject.getAsJsonObject());
            result.addAllKv(keyValueList);
            return result.build();
        } else {
            throw new JsonSyntaxException(CAN_T_PARSE_VALUE + jsonObject);
        }
    }

    public static JsonElement toJson(TransportProtos.ToDeviceRpcRequestMsg msg, boolean includeRequestId) {
        JsonObject result = new JsonObject();
        if (includeRequestId) {
            result.addProperty("id", msg.getRequestId());
        }
        result.addProperty("method", msg.getMethodName());
        result.add("params", new JsonParser().parse(msg.getParams()));
        return result;
    }

    private static void parseObject(PostTelemetryMsg.Builder builder, long systemTs, JsonObject jo) {
        if (jo.has("ts") && jo.has("values")) {
            parseWithTs(builder, jo);
        } else {
            parseWithoutTs(builder, systemTs, jo);
        }
    }

    private static void devinfoObject(PostTelemetryMsg.Builder builder, long systemTs, JsonObject jo)  {
        if (jo.has("ts") && jo.has("values")) {
            devinfoWithTs(builder, jo);
        } else if(jo.has("i") && jo.has("d1") && jo.has("d2") && jo.has("d3")){
            String current1 = jo.get("d1").getAsString();
            String current2 = jo.get("d1").getAsString();
            String current3 = jo.get("d1").getAsString();
            String[] arr1 = current1.split("\\|");
            String[] arr2 = current2.split("\\|");
            String[] arr3 = current3.split("\\|");
            int length = arr1.length < arr2.length ? arr1.length : arr2.length;
            length = length < arr3.length ? length : arr3.length;
            String strTs = jo.get("t").getAsString();
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
            for(int i =0; length > i; i++){
                Date date = null;
                try {
                    date = simpleDateFormat.parse(strTs);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                long ts = date.getTime()+(long)(15000 / length)*i + 1000 * 60 * 60 * 8;;
                    String data = "{\"ts\":"+ts+",\"values\":{\"current1\":\""+ arr1[i]
                                +"\",\"current2\":\""+arr2[i]+"\",\"current3\":\""+arr3[i]+"\",\"lac_cellId\":\""+jo.get("l").getAsString()+"\",\"ccid\":\""+jo.get("i").getAsString()+"\"}}";
                    parseWithTs(builder, new JsonParser().parse(data).getAsJsonObject());
                }
            }
        else if(jo.has("i") && jo.has("p1")){
            String[] plcData = getPlcData(jo);
            for (String plcDatum : plcData) {
                parseWithTs(builder, new JsonParser().parse(plcDatum).getAsJsonObject());
            }
        }else {
            parseWithoutTs(builder, systemTs, jo);
        }
    }

    private  static String [] getPlcData(JsonObject jo)  {
        String strTs = jo.get("t").getAsString();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        Date date = null;
        try {
            date = simpleDateFormat.parse(strTs);
        } catch (ParseException e) {
            e.printStackTrace();
        }
        if (jo.has("p8" ) && !jo.has("p14")){
            String [] dataArr = new String[8];
            for (int i = 1 ; i < 9; i++){
                long ts = date.getTime()+(long)(15000 / 8) * i + 1000 * 60 * 60 * 8;
                String datas = "{\"ts\":"+ts+",\"values\":{\"plcData\":\""+ jo.get("p"+i).getAsString()
                        +"\",\"lac_cellId\":\""+jo.get("l").getAsString()+"\",\"ccid\":\""+jo.get("i").getAsString()+"\"}}";
                dataArr[i-1] = datas;
            }
            return dataArr;
        }else {
            String [] dataArr = new String[14];
            for (int i = 1 ; i < 15; i++){
                long ts = date.getTime()+ 750 + 1000 * 60 * 60 * 8 ;
                String datas = "{\"ts\":"+ts+",\"values\":{\"plcData"+i+"\":\"p"+ jo.get("p"+i).getAsString()
                        +"\",\"lac_cellId\":\""+jo.get("l").getAsString()+"\",\"ccid\":\""+jo.get("i").getAsString()+"\"}}";
                dataArr[i-1] = datas;
            }
            return dataArr;
        }
    }


    private static void parseWithoutTs(PostTelemetryMsg.Builder request, long systemTs, JsonObject jo) {
        TsKvListProto.Builder builder = TsKvListProto.newBuilder();
        builder.setTs(systemTs);
        builder.addAllKv(parseProtoValues(jo));
        request.addTsKvList(builder.build());
    }

    private static void parseWithTs(PostTelemetryMsg.Builder request, JsonObject jo) {
        TsKvListProto.Builder builder = TsKvListProto.newBuilder();
        builder.setTs(jo.get("ts").getAsLong());
        builder.addAllKv(parseProtoValues(jo.get("values").getAsJsonObject()));
        request.addTsKvList(builder.build());
    }

    private static void devinfoWithTs(PostTelemetryMsg.Builder request, JsonObject jo) {
        try {
        String strTs = jo.get("ts").getAsString();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        Date date = simpleDateFormat.parse(strTs);
        long ts = date.getTime();
        TsKvListProto.Builder builder = TsKvListProto.newBuilder();
        builder.setTs(ts);
        builder.addAllKv(parseProtoValues(jo.get("values").getAsJsonObject()));
        request.addTsKvList(builder.build());
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    private static List<KeyValueProto> parseProtoValues(JsonObject valuesObject) {
        List<KeyValueProto> result = new ArrayList<>();
        for (Entry<String, JsonElement> valueEntry : valuesObject.entrySet()) {
            JsonElement element = valueEntry.getValue();
            if (element.isJsonPrimitive()) {
                JsonPrimitive value = element.getAsJsonPrimitive();
                if (value.isString()) {
                    if (maxStringValueLength > 0 && value.getAsString().length() > maxStringValueLength) {
                        String message = String.format("String value length [%d] for key [%s] is greater than maximum allowed [%d]", value.getAsString().length(), valueEntry.getKey(), maxStringValueLength);
                        throw new JsonSyntaxException(message);
                    }
                    if (isTypeCastEnabled && NumberUtils.isParsable(value.getAsString())) {
                        try {
                            result.add(buildNumericKeyValueProto(value, valueEntry.getKey()));
                        } catch (RuntimeException th) {
                            result.add(KeyValueProto.newBuilder().setKey(valueEntry.getKey()).setType(KeyValueType.STRING_V)
                                    .setStringV(value.getAsString()).build());
                        }
                    } else {
                        result.add(KeyValueProto.newBuilder().setKey(valueEntry.getKey()).setType(KeyValueType.STRING_V)
                                .setStringV(value.getAsString()).build());
                    }
                } else if (value.isBoolean()) {
                    result.add(KeyValueProto.newBuilder().setKey(valueEntry.getKey()).setType(KeyValueType.BOOLEAN_V)
                            .setBoolV(value.getAsBoolean()).build());
                } else if (value.isNumber()) {
                    result.add(buildNumericKeyValueProto(value, valueEntry.getKey()));
                } else if (!value.isJsonNull()) {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + value);
                }
            } else if (!element.isJsonNull()) {
                throw new JsonSyntaxException(CAN_T_PARSE_VALUE + element);
            }
        }
        return result;
    }

    private static KeyValueProto buildNumericKeyValueProto(JsonPrimitive value, String key) {
        if (value.getAsString().contains(".")) {
            return KeyValueProto.newBuilder()
                    .setKey(key)
                    .setType(KeyValueType.DOUBLE_V)
                    .setDoubleV(value.getAsDouble())
                    .build();
        } else {
            try {
                long longValue = Long.parseLong(value.getAsString());
                return KeyValueProto.newBuilder().setKey(key).setType(KeyValueType.LONG_V)
                        .setLongV(longValue).build();
            } catch (NumberFormatException e) {
                throw new JsonSyntaxException("Big integer values are not supported!");
            }
        }
    }

    public static TransportProtos.ToServerRpcRequestMsg convertToServerRpcRequest(JsonElement json, int requestId) throws JsonSyntaxException {
        JsonObject object = json.getAsJsonObject();
        return TransportProtos.ToServerRpcRequestMsg.newBuilder().setRequestId(requestId).setMethodName(object.get("method").getAsString()).setParams(GSON.toJson(object.get("params"))).build();
    }

    private static void parseNumericValue(List<KvEntry> result, Entry<String, JsonElement> valueEntry, JsonPrimitive value) {
        if (value.getAsString().contains(".")) {
            result.add(new DoubleDataEntry(valueEntry.getKey(), value.getAsDouble()));
        } else {
            try {
                long longValue = Long.parseLong(value.getAsString());
                result.add(new LongDataEntry(valueEntry.getKey(), longValue));
            } catch (NumberFormatException e) {
                throw new JsonSyntaxException("Big integer values are not supported!");
            }
        }
    }

    public static JsonObject toJson(GetAttributeResponseMsg payload) {
        JsonObject result = new JsonObject();
        if (payload.getClientAttributeListCount() > 0) {
            JsonObject attrObject = new JsonObject();
            payload.getClientAttributeListList().forEach(addToObjectFromProto(attrObject));
            result.add("client", attrObject);
        }
        if (payload.getSharedAttributeListCount() > 0) {
            JsonObject attrObject = new JsonObject();
            payload.getSharedAttributeListList().forEach(addToObjectFromProto(attrObject));
            result.add("shared", attrObject);
        }
        if (payload.getDeletedAttributeKeysCount() > 0) {
            JsonArray attrObject = new JsonArray();
            payload.getDeletedAttributeKeysList().forEach(attrObject::add);
            result.add("deleted", attrObject);
        }
        return result;
    }

    public static JsonElement toJson(AttributeUpdateNotificationMsg payload) {
        JsonObject result = new JsonObject();
        if (payload.getSharedUpdatedCount() > 0) {
            payload.getSharedUpdatedList().forEach(addToObjectFromProto(result));
        }
        if (payload.getSharedDeletedCount() > 0) {
            JsonArray attrObject = new JsonArray();
            payload.getSharedDeletedList().forEach(attrObject::add);
            result.add("deleted", attrObject);
        }
        return result;
    }

    public static JsonObject toJson(AttributesKVMsg payload, boolean asMap) {
        JsonObject result = new JsonObject();
        if (asMap) {
            if (!payload.getClientAttributes().isEmpty()) {
                JsonObject attrObject = new JsonObject();
                payload.getClientAttributes().forEach(addToObject(attrObject));
                result.add("client", attrObject);
            }
            if (!payload.getSharedAttributes().isEmpty()) {
                JsonObject attrObject = new JsonObject();
                payload.getSharedAttributes().forEach(addToObject(attrObject));
                result.add("shared", attrObject);
            }
        } else {
            payload.getClientAttributes().forEach(addToObject(result));
            payload.getSharedAttributes().forEach(addToObject(result));
        }
        if (!payload.getDeletedAttributes().isEmpty()) {
            JsonArray attrObject = new JsonArray();
            payload.getDeletedAttributes().forEach(addToObject(attrObject));
            result.add("deleted", attrObject);
        }
        return result;
    }

    public static JsonObject getJsonObjectForGateway(String deviceName, TransportProtos.GetAttributeResponseMsg responseMsg) {
        JsonObject result = new JsonObject();
        result.addProperty("id", responseMsg.getRequestId());
        result.addProperty(DEVICE_PROPERTY, deviceName);
        if (responseMsg.getClientAttributeListCount() > 0) {
            addValues(result, responseMsg.getClientAttributeListList());
        }
        if (responseMsg.getSharedAttributeListCount() > 0) {
            addValues(result, responseMsg.getSharedAttributeListList());
        }
        return result;
    }

    public static JsonObject getJsonObjectForGateway(String deviceName, AttributeUpdateNotificationMsg notificationMsg) {
        JsonObject result = new JsonObject();
        result.addProperty(DEVICE_PROPERTY, deviceName);
        result.add("data", toJson(notificationMsg));
        return result;
    }

    private static void addValues(JsonObject result, List<TransportProtos.TsKvProto> kvList) {
        if (kvList.size() == 1) {
            addValueToJson(result, "value", kvList.get(0).getKv());
        } else {
            JsonObject values;
            if (result.has("values")) {
                values = result.get("values").getAsJsonObject();
            } else {
                values = new JsonObject();
                result.add("values", values);
            }
            kvList.forEach(value -> addValueToJson(values, value.getKv().getKey(), value.getKv()));
        }
    }

    private static void addValueToJson(JsonObject json, String name, TransportProtos.KeyValueProto entry) {
        switch (entry.getType()) {
            case BOOLEAN_V:
                json.addProperty(name, entry.getBoolV());
                break;
            case STRING_V:
                json.addProperty(name, entry.getStringV());
                break;
            case DOUBLE_V:
                json.addProperty(name, entry.getDoubleV());
                break;
            case LONG_V:
                json.addProperty(name, entry.getLongV());
                break;
        }
    }

    private static Consumer<AttributeKey> addToObject(JsonArray result) {
        return key -> result.add(key.getAttributeKey());
    }

    private static Consumer<TsKvProto> addToObjectFromProto(JsonObject result) {
        return de -> {
            JsonPrimitive value;
            switch (de.getKv().getType()) {
                case BOOLEAN_V:
                    value = new JsonPrimitive(de.getKv().getBoolV());
                    break;
                case DOUBLE_V:
                    value = new JsonPrimitive(de.getKv().getDoubleV());
                    break;
                case LONG_V:
                    value = new JsonPrimitive(de.getKv().getLongV());
                    break;
                case STRING_V:
                    value = new JsonPrimitive(de.getKv().getStringV());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported data type: " + de.getKv().getType());
            }
            result.add(de.getKv().getKey(), value);
        };
    }

    private static Consumer<AttributeKvEntry> addToObject(JsonObject result) {
        return de -> {
            JsonPrimitive value;
            switch (de.getDataType()) {
                case BOOLEAN:
                    value = new JsonPrimitive(de.getBooleanValue().get());
                    break;
                case DOUBLE:
                    value = new JsonPrimitive(de.getDoubleValue().get());
                    break;
                case LONG:
                    value = new JsonPrimitive(de.getLongValue().get());
                    break;
                case STRING:
                    value = new JsonPrimitive(de.getStrValue().get());
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported data type: " + de.getDataType());
            }
            result.add(de.getKey(), value);
        };
    }

    public static JsonElement toJson(TransportProtos.ToServerRpcResponseMsg msg) {
        if (StringUtils.isEmpty(msg.getError())) {
            return new JsonParser().parse(msg.getPayload());
        } else {
            JsonObject errorMsg = new JsonObject();
            errorMsg.addProperty("error", msg.getError());
            return errorMsg;
        }
    }

    public static JsonElement toErrorJson(String errorMsg) {
        JsonObject error = new JsonObject();
        error.addProperty("error", errorMsg);
        return error;
    }

    public static JsonElement toGatewayJson(String deviceName, TransportProtos.ToDeviceRpcRequestMsg rpcRequest) {
        JsonObject result = new JsonObject();
        result.addProperty(DEVICE_PROPERTY, deviceName);
        result.add("data", JsonConverter.toJson(rpcRequest, true));
        return result;
    }

    public static Set<AttributeKvEntry> convertToAttributes(JsonElement element) {
        Set<AttributeKvEntry> result = new HashSet<>();
        long ts = System.currentTimeMillis();
        result.addAll(parseValues(element.getAsJsonObject()).stream().map(kv -> new BaseAttributeKvEntry(kv, ts)).collect(Collectors.toList()));
        return result;
    }

    private static List<KvEntry> parseValues(JsonObject valuesObject) {
        List<KvEntry> result = new ArrayList<>();
        for (Entry<String, JsonElement> valueEntry : valuesObject.entrySet()) {
            JsonElement element = valueEntry.getValue();
            if (element.isJsonPrimitive()) {
                JsonPrimitive value = element.getAsJsonPrimitive();
                if (value.isString()) {
                    if (maxStringValueLength > 0 && value.getAsString().length() > maxStringValueLength) {
                        String message = String.format("String value length [%d] for key [%s] is greater than maximum allowed [%d]", value.getAsString().length(), valueEntry.getKey(), maxStringValueLength);
                        throw new JsonSyntaxException(message);
                    }
                    if (isTypeCastEnabled && NumberUtils.isParsable(value.getAsString())) {
                        try {
                            parseNumericValue(result, valueEntry, value);
                        } catch (RuntimeException th) {
                            result.add(new StringDataEntry(valueEntry.getKey(), value.getAsString()));
                        }
                    } else {
                        result.add(new StringDataEntry(valueEntry.getKey(), value.getAsString()));
                    }
                } else if (value.isBoolean()) {
                    result.add(new BooleanDataEntry(valueEntry.getKey(), value.getAsBoolean()));
                } else if (value.isNumber()) {
                    parseNumericValue(result, valueEntry, value);
                } else {
                    throw new JsonSyntaxException(CAN_T_PARSE_VALUE + value);
                }
            } else {
                throw new JsonSyntaxException(CAN_T_PARSE_VALUE + element);
            }
        }
        return result;
    }

    public static Map<Long, List<KvEntry>> convertToTelemetry(JsonElement jsonElement, long systemTs) throws JsonSyntaxException {
        Map<Long, List<KvEntry>> result = new HashMap<>();
        convertToTelemetry(jsonElement, systemTs, result, null);
        return result;
    }

    private static void parseObject(Map<Long, List<KvEntry>> result, long systemTs, JsonObject jo) {
        if (jo.has("ts") && jo.has("values")) {
            parseWithTs(result, jo);
        } else {
            parseWithoutTs(result, systemTs, jo);
        }
    }

    private static void devinfoObject(Map<Long, List<KvEntry>> result, long systemTs, JsonObject jo) {
        if (jo.has("ts") && jo.has("values")) {
            devinfoWithTs(result, jo);
        }else if(jo.has("i") && jo.has("d1") && jo.has("d2") && jo.has("d3")){
            String current1 = jo.get("d1").getAsString();
            String current2 = jo.get("d1").getAsString();
            String current3 = jo.get("d1").getAsString();
            String[] arr1 = current1.split("\\|");
            String[] arr2 = current2.split("\\|");
            String[] arr3 = current3.split("\\|");
            int length = arr1.length < arr2.length ? arr1.length : arr2.length;
            length = length < arr3.length ? length : arr3.length;
            for(int i =0; length > i; i++){
                try {
                    String strTs = jo.get("t").getAsString();
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                    simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
                    Date date = simpleDateFormat.parse(strTs);
                    long ts = date.getTime()+(long)(15000 / length)*i + 1000 * 60 * 60 * 8;
                    String data = "{\"ts\":"+ts+",\"values\":{\"current1\":\""+ arr1[i]
                            +"\",\"current2\":\""+arr2[i]+"\",\"current3\":\""+arr3[i]+"\",\"lac_cellId\":\""+jo.get("l").getAsString()+"\",\"ccid\":\""+jo.get("i").getAsString()+"\"}}";
                    parseWithTs(result, new JsonParser().parse(data).getAsJsonObject());
                } catch (ParseException e){
                    e.printStackTrace();
                }
            }
        } else {
            parseWithoutTs(result, systemTs, jo);
        }
    }



    private static void parseWithoutTs(Map<Long, List<KvEntry>> result, long systemTs, JsonObject jo) {
        for (KvEntry entry : parseValues(jo)) {
            result.computeIfAbsent(systemTs, tmp -> new ArrayList<>()).add(entry);
        }
    }

    public static void parseWithTs(Map<Long, List<KvEntry>> result, JsonObject jo) {
        long ts = jo.get("ts").getAsLong();
        JsonObject valuesObject = jo.get("values").getAsJsonObject();
        for (KvEntry entry : parseValues(valuesObject)) {
            result.computeIfAbsent(ts, tmp -> new ArrayList<>()).add(entry);
        }
    }

    //互感器数据处理
    public static void devinfoWithTs(Map<Long, List<KvEntry>> result, JsonObject jo) {
        try {
        String strTs = jo.get("ts").getAsString();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT+8:00"));
        Date date = simpleDateFormat.parse(strTs);
        long ts = date.getTime();
        JsonObject valuesObject = jo.get("values").getAsJsonObject();
        for (KvEntry entry : parseValues(valuesObject)) {
            result.computeIfAbsent(ts, tmp -> new ArrayList<>()).add(entry);
        }
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    public static void setTypeCastEnabled(boolean enabled) {
        isTypeCastEnabled = enabled;
    }

    public static void setMaxStringValueLength(int length) {
        maxStringValueLength = length;
    }

}
