package com.bscllc.ai.text.model.input;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;

import com.bscllc.ai.text.model.datamodel.YellowTaxi;

/**
 * Reader class for Yellow Taxi Parquet files.
 * Reads Parquet files like yellow_tripdata_2025_01.parquet, validates the schema,
 * and outputs YellowTaxi objects.
 */
public class YellowReader {
    private static final Logger logger = LogManager.getLogger(YellowReader.class);
    
    private static final DateTimeFormatter DATETIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static final List<String> REQUIRED_FIELDS = List.of(
        "VendorID",
        "tpep_pickup_datetime",
        "tpep_dropoff_datetime",
        "passenger_count",
        "trip_distance",
        "RatecodeID",
        "store_and_fwd_flag",
        "PULocationID",
        "DOLocationID",
        "payment_type",
        "fare_amount",
        "extra",
        "mta_tax",
        "tip_amount",
        "tolls_amount",
        "improvement_surcharge",
        "total_amount",
        "congestion_surcharge"
    );
    
    private static final List<String> OPTIONAL_FIELDS = List.of(
        "airport_fee",
        "cbd_congestion_fee"
    );

    /**
     * Reads a Parquet file and returns a stream of YellowTaxi objects.
     *
     * @param filePath the path to the Parquet file
     * @return a stream of YellowTaxi objects
     * @throws IOException if there's an error reading the file
     * @throws SchemaValidationException if the schema doesn't match expected fields
     */
    public Stream<YellowTaxi> read(java.nio.file.Path filePath) throws IOException, SchemaValidationException {
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        logger.info("Reading Parquet file: {}", filePath);
        
        // Configure Hadoop to use local filesystem without security
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);
        
        Path hadoopPath = new Path(filePath.toUri());
        InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);
        
        // Validate schema first
        try (ParquetReader<GenericRecord> validationReader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            GenericRecord firstRecord = validationReader.read();
            if (firstRecord != null) {
                validateSchema(firstRecord);
            } else {
                logger.warn("Parquet file is empty: {}", filePath);
                return Stream.empty();
            }
        } catch (Exception e) {
            throw new SchemaValidationException("Failed to validate schema: " + e.getMessage(), e);
        }

        // Create reader for actual data reading
        final ParquetReader<GenericRecord> reader = 
            AvroParquetReader.<GenericRecord>builder(inputFile).build();

        // Create stream with proper resource management
        // Use onClose to ensure reader is closed even if stream is not fully consumed
        return Stream.generate(() -> {
            try {
                GenericRecord record = reader.read();
                if (record == null) {
                    return null;
                }
                return convertToYellowTaxi(record);
            } catch (IOException e) {
                logger.error("Error reading Parquet record", e);
                throw new RuntimeException("Error reading Parquet file", e);
            }
        })
        .takeWhile(taxi -> taxi != null)
        .onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                logger.error("Error closing Parquet reader", e);
            }
        });
    }

    /**
     * Reads a Parquet file and returns a list of YellowTaxi objects.
     *
     * @param filePath the path to the Parquet file
     * @return a list of YellowTaxi objects
     * @throws IOException if there's an error reading the file
     * @throws SchemaValidationException if the schema doesn't match expected fields
     */
    public List<YellowTaxi> readAll(java.nio.file.Path filePath) throws IOException, SchemaValidationException {
        try (Stream<YellowTaxi> stream = read(filePath)) {
            return stream.toList();
        }
    }

    /**
     * Validates that the Parquet file schema contains all expected fields.
     *
     * @param record a sample record from the Parquet file
     * @throws SchemaValidationException if the schema is invalid
     */
    private void validateSchema(GenericRecord record) throws SchemaValidationException {
        org.apache.avro.Schema schema = record.getSchema();
        List<String> missingFields = new ArrayList<>();
        
        // Check required fields
        for (String requiredField : REQUIRED_FIELDS) {
            if (schema.getField(requiredField) == null) {
                missingFields.add(requiredField);
            }
        }
        
        if (!missingFields.isEmpty()) {
            throw new SchemaValidationException(
                String.format("Schema validation failed. Missing required fields: %s", missingFields)
            );
        }
        
        // Log optional fields status
        long optionalFieldsFound = OPTIONAL_FIELDS.stream()
            .filter(field -> schema.getField(field) != null)
            .count();
        
        logger.info("Schema validation passed. All {} required fields found. {} optional fields found.",
            REQUIRED_FIELDS.size(), optionalFieldsFound);
    }

    /**
     * Converts a GenericRecord from Parquet to a YellowTaxi object.
     *
     * @param record the Parquet record
     * @return a YellowTaxi object
     */
    private YellowTaxi convertToYellowTaxi(GenericRecord record) {
        org.apache.avro.Schema schema = record.getSchema();
        return new YellowTaxi(
            getInteger(record, "VendorID"),
            parseDateTime(record, "tpep_pickup_datetime"),
            parseDateTime(record, "tpep_dropoff_datetime"),
            getInteger(record, "passenger_count"),
            getDouble(record, "trip_distance"),
            getInteger(record, "RatecodeID"),
            getString(record, "store_and_fwd_flag"),
            getInteger(record, "PULocationID"),
            getInteger(record, "DOLocationID"),
            getInteger(record, "payment_type"),
            getDouble(record, "fare_amount"),
            getDouble(record, "extra"),
            getDouble(record, "mta_tax"),
            getDouble(record, "tip_amount"),
            getDouble(record, "tolls_amount"),
            getDouble(record, "improvement_surcharge"),
            getDouble(record, "total_amount"),
            getDouble(record, "congestion_surcharge"),
            getDoubleOptional(record, schema, "airport_fee"),
            getDoubleOptional(record, schema, "cbd_congestion_fee")
        );
    }

    /**
     * Gets an Integer value from a record, handling null values.
     */
    private Integer getInteger(GenericRecord record, String fieldName) {
        Object value = record.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Long) {
            return ((Long) value).intValue();
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        throw new IllegalArgumentException(
            String.format("Field %s is not a number: %s", fieldName, value.getClass())
        );
    }

    /**
     * Gets a Double value from a record, handling null values.
     */
    private Double getDouble(GenericRecord record, String fieldName) {
        Object value = record.get(fieldName);
        if (value == null) {
            return null;
        }
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Float) {
            return ((Float) value).doubleValue();
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        throw new IllegalArgumentException(
            String.format("Field %s is not a number: %s", fieldName, value.getClass())
        );
    }

    /**
     * Gets a Double value from a record, handling null values and optional fields.
     * Returns null if the field doesn't exist in the schema.
     */
    private Double getDoubleOptional(GenericRecord record, org.apache.avro.Schema schema, String fieldName) {
        if (schema.getField(fieldName) == null) {
            return null;
        }
        return getDouble(record, fieldName);
    }

    /**
     * Gets a String value from a record, handling null values.
     */
    private String getString(GenericRecord record, String fieldName) {
        Object value = record.get(fieldName);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Parses a datetime from a record to LocalDateTime.
     * Handles both string formats and timestamp (microseconds since epoch).
     */
    private LocalDateTime parseDateTime(GenericRecord record, String fieldName) {
        Object value = record.get(fieldName);
        if (value == null) {
            return null;
        }
        
        // Handle timestamp (Long or Integer) - microseconds since epoch
        if (value instanceof Long) {
            long microseconds = (Long) value;
            long seconds = microseconds / 1_000_000;
            long nanos = (microseconds % 1_000_000) * 1_000;
            return LocalDateTime.ofEpochSecond(seconds, (int) nanos, java.time.ZoneOffset.UTC);
        }
        if (value instanceof Integer) {
            long microseconds = ((Integer) value).longValue();
            long seconds = microseconds / 1_000_000;
            long nanos = (microseconds % 1_000_000) * 1_000;
            return LocalDateTime.ofEpochSecond(seconds, (int) nanos, java.time.ZoneOffset.UTC);
        }
        
        // Handle string formats
        String dateTimeStr = value.toString();
        try {
            // Try standard format first
            return LocalDateTime.parse(dateTimeStr, DATETIME_FORMATTER);
        } catch (Exception e) {
            // Try ISO format
            try {
                return LocalDateTime.parse(dateTimeStr);
            } catch (Exception e2) {
                logger.warn("Failed to parse datetime for field {}: {}", fieldName, dateTimeStr);
                throw new IllegalArgumentException(
                    String.format("Cannot parse datetime for field %s: %s", fieldName, dateTimeStr), e2
                );
            }
        }
    }

    /**
     * Exception thrown when schema validation fails.
     */
    public static class SchemaValidationException extends Exception {
        public SchemaValidationException(String message) {
            super(message);
        }

        public SchemaValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

