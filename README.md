# kafka-connect-transform-jsonstring-to-map

A custom Kafka Connect **Single Message Transform (SMT)** for converting a JSON string field (e.g., `version_data`) into a nested map, with optional logic for:
- Parsing fractions like `"60000/1001"` into floats.
- Replacing invalid numeric strings (e.g. `"N/A"`) with default values (e.g. `0L`).

## Features

- Parses a JSON string field into a structured `Map`.
- Supports nested objects recursively.
- Handles edge cases like:
  - `"60000/1001"` → `59.94` (float)
  - `"N/A"` for numeric fields → `0`, `0L`, etc.

## Configuration Example

```properties
transforms=ExtractKey,ExtractAfterField,JsonStringToMapTransform

transforms.ExtractAfterField.type=org.apache.kafka.connect.transforms.ExtractField$Value
transforms.ExtractAfterField.field=after

transforms.ExtractKey.type=org.apache.kafka.connect.transforms.ExtractField$Key
transforms.ExtractKey.field=id

transforms.JsonStringToMapTransform.type=com.picflow.transforms.JsonStringToMap$Value
transforms.JsonStringToMapTransform.field.name=version_data
```

## Build with Maven
```
mvn clean package
```