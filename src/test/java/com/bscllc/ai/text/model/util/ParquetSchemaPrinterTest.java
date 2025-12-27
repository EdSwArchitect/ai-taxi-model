package com.bscllc.ai.text.model.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Test class to print the schema of a Parquet file in JSON format.
 */
@DisplayName("Parquet Schema Printer Tests")
class ParquetSchemaPrinterTest {

    private static final Logger logger = LogManager.getLogger(ParquetSchemaPrinterTest.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("Should print schema of yellow_tripdata parquet file in JSON")
    void testPrintYellowTripdataSchema() throws Exception {
        Path[] possiblePaths = {
            Paths.get("yellow_tripdata_2025_01.parquet"),
            Paths.get("data/yellow_tripdata_2025_01.parquet"),
            Paths.get("../yellow_tripdata_2025_01.parquet"),
            Paths.get("src/test/resources/yellow_tripdata_2025_01.parquet"),
            Paths.get("src/main/resources/yellow_tripdata_2025_01.parquet")
        };

        Path parquetFile = findFile(possiblePaths);
        if (parquetFile == null) {
            logger.warn("Skipping test: yellow_tripdata_2025_01.parquet not found in any of the expected locations");
            return;
        }

        logger.info("Found yellow_tripdata file at: {}", parquetFile);
        printSchema(parquetFile);
    }

    @Test
    @DisplayName("Should print schema of green_tripdata parquet file in JSON")
    void testPrintGreenTripdataSchema() throws Exception {
        Path[] possiblePaths = {
            Paths.get("green_tripdata_2025_01.parquet"),
            Paths.get("data/green_tripdata_2025_01.parquet"),
            Paths.get("../green_tripdata_2025_01.parquet"),
            Paths.get("src/test/resources/green_tripdata_2025_01.parquet"),
            Paths.get("src/main/resources/green_tripdata_2025_01.parquet")
        };

        Path parquetFile = findFile(possiblePaths);
        if (parquetFile == null) {
            logger.warn("Skipping test: green_tripdata_2025_01.parquet not found in any of the expected locations");
            return;
        }

        logger.info("Found green_tripdata file at: {}", parquetFile);
        printSchema(parquetFile);
    }

    /**
     * Prints the schema of a Parquet file in JSON format.
     *
     * @param parquetFile the path to the Parquet file
     * @throws IOException if there's an error reading the file
     */
    private void printSchema(Path parquetFile) throws IOException {
        logger.info("========================================");
        logger.info("Printing schema for Parquet file: {}", parquetFile);
        logger.info("========================================");

        // Configure Hadoop to use local filesystem
        logger.debug("Configuring Hadoop for local filesystem");
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);

        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(parquetFile.toUri());
        InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);
        logger.debug("Created Hadoop InputFile from path: {}", hadoopPath);

        // Read schema from first record
        logger.debug("Reading first record to extract schema");
        Schema avroSchema;
        try (ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            GenericRecord firstRecord = reader.read();
            if (firstRecord == null) {
                logger.error("Parquet file is empty: {}", parquetFile);
                throw new IOException("Parquet file is empty: " + parquetFile);
            }
            avroSchema = firstRecord.getSchema();
            logger.info("Successfully extracted Avro schema: {} (type: {})", 
                avroSchema.getName(), avroSchema.getType());
        }

        // Convert Avro schema to JSON
        logger.debug("Converting schema to JSON format");
        String schemaJson = convertSchemaToJson(avroSchema);
        
        logger.info("Schema (JSON):\n{}", schemaJson);
        logger.info("========================================");
    }

    /**
     * Converts an Avro schema to a formatted JSON string.
     *
     * @param schema the Avro schema
     * @return JSON string representation of the schema
     */
    private String convertSchemaToJson(Schema schema) {
        try {
            // Avro schema already has a toString() that returns JSON
            // But we can also use Jackson to pretty-print it
            String schemaString = schema.toString();
            logger.debug("Parsing schema JSON string (length: {} chars)", schemaString.length());
            
            // Parse and pretty-print using Jackson
            ObjectNode schemaNode = (ObjectNode) objectMapper.readTree(schemaString);
            String prettyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schemaNode);
            logger.debug("Successfully formatted schema JSON");
            return prettyJson;
        } catch (Exception e) {
            // Fallback to direct schema toString if JSON parsing fails
            logger.warn("Failed to pretty-print schema JSON, using direct toString(): {}", e.getMessage());
            return schema.toString();
        }
    }

    /**
     * Finds the first existing file from the given paths.
     *
     * @param paths array of possible file paths
     * @return the first existing file path, or null if none found
     */
    private Path findFile(Path[] paths) {
        logger.debug("Searching for file in {} possible locations", paths.length);
        for (Path path : paths) {
            logger.trace("Checking path: {}", path);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                logger.debug("Found file at: {}", path);
                return path;
            }
        }
        logger.debug("File not found in any of the searched locations");
        return null;
    }
}

