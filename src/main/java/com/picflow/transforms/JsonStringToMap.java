package com.picflow.transforms;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.ConnectRecord;
import org.apache.kafka.connect.data.*;
import org.apache.kafka.connect.transforms.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public abstract class JsonStringToMap<R extends ConnectRecord<R>> implements Transformation<R> {

    private static final Logger log = LoggerFactory.getLogger(JsonStringToMap.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private String fieldName;

    @Override
    public void configure(Map<String, ?> configs) {
        this.fieldName = (String) configs.get("field.name");
        if (this.fieldName == null || this.fieldName.isEmpty()) {
            throw new IllegalArgumentException("field.name must be provided");
        }
    }

    @Override
    public R apply(R record) {
        if (isTombstone(record)) {
            return record;
        }

        Object value = getOperatingValue(record);
        if (!(value instanceof Map)) {
            return record;
        }

        Map<String, Object> valueMap = (Map<String, Object>) value;
        Object rawJson = valueMap.get(fieldName);

        if (!(rawJson instanceof String)) {
            return record;
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue((String) rawJson, Map.class);

            Object dataObj = parsed.get("data");
            if (dataObj instanceof Map) {
                Map<String, Object> dataMap = (Map<String, Object>) dataObj;
                Object frameRate = dataMap.get("frame_rate");
                if (frameRate instanceof String && ((String) frameRate).contains("/")) {
                    try {
                        String[] parts = ((String) frameRate).split("/");
                        double numerator = Double.parseDouble(parts[0]);
                        double denominator = Double.parseDouble(parts[1]);
                        double result = numerator / denominator;
                        dataMap.put("frame_rate", result);
                    } catch (Exception e) {
                        log.warn("Failed to parse frame_rate '{}'", frameRate);
                    }
                }
                Object frameCount = dataMap.get("frame_count");
                if (frameCount instanceof String) {
                    String fc = ((String) frameCount).trim();
                    if ("N/A".equalsIgnoreCase(fc)) {
                        log.debug("Replacing frame_count 'N/A' with 0");
                        dataMap.put("frame_count", 0L);
                    } else {
                        try {
                            dataMap.put("frame_count", Long.parseLong(fc));
                        } catch (NumberFormatException e) {
                            log.warn("Failed to parse frame_count '{}', setting to 0", frameCount);
                            dataMap.put("frame_count", 0L);
                        }
                    }
                }
            }

            valueMap.put(fieldName, parsed);

            return newRecord(record, null, valueMap);
        } catch (Exception e) {
            log.error("Failed to parse JSON for '{}': {}", fieldName, e.getMessage(), e);
            return record;
        }
    }

    @Override
    public ConfigDef config() {
        return new ConfigDef().define("field.name", ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "The name of the field containing the JSON string.");
    }

    @Override
    public void close() {}

    protected abstract Object getOperatingValue(R record);
    protected abstract R newRecord(R record, Schema updatedSchema, Object updatedValue);
    protected abstract boolean isTombstone(R record);

    public static class Value<R extends ConnectRecord<R>> extends JsonStringToMap<R> {
        @Override
        protected Object getOperatingValue(R record) {
            return record.value();
        }

        @Override
        protected R newRecord(R record, Schema updatedSchema, Object updatedValue) {
            return record.newRecord(
                    record.topic(),
                    record.kafkaPartition(),
                    record.keySchema(),
                    record.key(),
                    record.valueSchema(),
                    updatedValue,
                    record.timestamp()
            );
        }

        @Override
        protected boolean isTombstone(R record) {
            return record.value() == null;
        }
    }
}
