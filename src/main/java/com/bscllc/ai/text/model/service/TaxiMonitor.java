package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;

import com.bscllc.ai.text.model.datamodel.GreenTaxi;
import com.bscllc.ai.text.model.datamodel.YellowTaxi;
import com.bscllc.ai.text.model.input.GreenReader;
import com.bscllc.ai.text.model.input.YellowReader;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service that monitors a directory for Parquet files, processes them,
 * and indexes the data into OpenSearch.
 */
@ApplicationScoped
public class TaxiMonitor {

    private static final Logger logger = LogManager.getLogger(TaxiMonitor.class);
    private static final String PARQUET_EXTENSION = ".parquet";
    private static final int BATCH_SIZE = 1000;

    @Inject
    OpenSearchService openSearchService;

    @Inject
    MeterRegistry meterRegistry;

    private final GreenReader greenReader = new GreenReader();
    private final YellowReader yellowReader = new YellowReader();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .findAndRegisterModules()
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    // Track processed files to avoid reprocessing
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();

    // Micrometer metrics
    private Counter filesProcessedCounter;
    private Counter yellowTaxiFilesCounter;
    private Counter yellowTaxiRecordsCounter;
    private Counter greenTaxiFilesCounter;
    private Counter greenTaxiRecordsCounter;
    private Counter erroredFilesCounter;

    @PostConstruct
    void initMetrics() {
        filesProcessedCounter = Counter.builder("taxi.monitor.files.processed")
                .description("Total number of files processed")
                .register(meterRegistry);
        
        yellowTaxiFilesCounter = Counter.builder("taxi.monitor.yellow.files")
                .description("Number of yellow taxi files processed")
                .register(meterRegistry);
        
        yellowTaxiRecordsCounter = Counter.builder("taxi.monitor.yellow.records")
                .description("Number of yellow taxi records processed")
                .register(meterRegistry);
        
        greenTaxiFilesCounter = Counter.builder("taxi.monitor.green.files")
                .description("Number of green taxi files processed")
                .register(meterRegistry);
        
        greenTaxiRecordsCounter = Counter.builder("taxi.monitor.green.records")
                .description("Number of green taxi records processed")
                .register(meterRegistry);
        
        erroredFilesCounter = Counter.builder("taxi.monitor.files.errored")
                .description("Number of files that failed processing")
                .register(meterRegistry);
    }

    /**
     * Scheduled task that monitors the input directory for new files.
     * Runs every 30 seconds.
     */
    @Scheduled(every = "30s")
    void monitorDirectory() {
        String inputDir = System.getProperty("taxi.monitor.input.dir", "./data/input");
        String errorDir = System.getProperty("taxi.monitor.error.dir", "./data/error");

        logger.info("Input path: {}", Path.of(inputDir).toAbsolutePath().normalize().toString());
        logger.info("Error path: {}", Path.of(errorDir).toAbsolutePath().normalize().toString());


        Path inputPath = Paths.get(inputDir);
        Path errorPath = Paths.get(errorDir);

        try {
            // Ensure directories exist
            Files.createDirectories(inputPath);
            Files.createDirectories(errorPath);

            // Process all files in the input directory
            try (Stream<Path> files = Files.list(inputPath)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> !processedFiles.contains(file.toString()))
                        .forEach(file -> {
                            try {
                                processFile(file, errorPath);
                            } catch (Exception e) {
                                logger.error("Error processing file: {}", file, e);
                            }
                        });
            }
        } catch (IOException e) {
            logger.error("Error monitoring directory: {}", inputDir, e);
        }
    }

    /**
     * Processes a single file: validates it, determines schema type,
     * and indexes to OpenSearch.
     *
     * @param file the file to process
     * @param errorDir the directory to move invalid files to
     */
    private void processFile(Path file, Path errorDir) {
        String fileName = file.getFileName().toString();
        logger.info("Processing file: {}", fileName);

        // Check if file is a Parquet file
        if (!fileName.toLowerCase().endsWith(PARQUET_EXTENSION)) {
            logger.warn("File {} is not a Parquet file, moving to error directory", fileName);
            try {
                moveToErrorDirectory(file, errorDir, "not_parquet");
            } catch (IOException e) {
                logger.error("Failed to move file to error directory: {}", fileName, e);
            }
            filesProcessedCounter.increment();
            erroredFilesCounter.increment();
            processedFiles.add(file.toString());
            return;
        }

        try {
            // Determine schema type by attempting to read with each reader
            SchemaType schemaType = detectSchemaType(file);

            if (schemaType == SchemaType.UNKNOWN) {
                logger.warn("File {} does not match Green or Yellow taxi schema, moving to error directory", fileName);
                moveToErrorDirectory(file, errorDir, "schema_mismatch");
                filesProcessedCounter.increment();
                erroredFilesCounter.increment();
                processedFiles.add(file.toString());
                return;
            }

            // Process based on schema type
            if (schemaType == SchemaType.GREEN) {
                processGreenTaxiFile(file);
                greenTaxiFilesCounter.increment();
            } else if (schemaType == SchemaType.YELLOW) {
                processYellowTaxiFile(file);
                yellowTaxiFilesCounter.increment();
            }

            filesProcessedCounter.increment();
            processedFiles.add(file.toString());
            logger.info("Successfully processed file: {}", fileName);

        } catch (Exception e) {
            logger.error("Error processing file: {}", fileName, e);
            try {
                moveToErrorDirectory(file, errorDir, "processing_error");
            } catch (IOException ioException) {
                logger.error("Failed to move file to error directory: {}", fileName, ioException);
            }
            filesProcessedCounter.increment();
            erroredFilesCounter.increment();
            processedFiles.add(file.toString());
        }
    }

    /**
     * Detects the schema type of a Parquet file by attempting to validate
     * against both Green and Yellow taxi schemas.
     *
     * @param file the Parquet file
     * @return the detected schema type
     */
    private SchemaType detectSchemaType(Path file) {
        try {
            // Try Green schema first
            if (isGreenTaxiSchema(file)) {
                return SchemaType.GREEN;
            }

            // Try Yellow schema
            if (isYellowTaxiSchema(file)) {
                return SchemaType.YELLOW;
            }

            return SchemaType.UNKNOWN;
        } catch (Exception e) {
            logger.error("Error detecting schema type for file: {}", file, e);
            return SchemaType.UNKNOWN;
        }
    }

    /**
     * Checks if a file matches the Green taxi schema.
     */
    private boolean isGreenTaxiSchema(Path file) {
        try {
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", "file:///");
            conf.setBoolean("fs.file.impl.disable.cache", true);

            org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(file.toUri());
            InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);

            try (ParquetReader<GenericRecord> reader = 
                    AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
                GenericRecord firstRecord = reader.read();
                if (firstRecord == null) {
                    return false;
                }

                org.apache.avro.Schema schema = firstRecord.getSchema();
                // Check for Green taxi specific field
                return schema.getField("lpep_pickup_datetime") != null &&
                       schema.getField("trip_type") != null;
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if a file matches the Yellow taxi schema.
     */
    private boolean isYellowTaxiSchema(Path file) {
        try {
            Configuration conf = new Configuration();
            conf.set("fs.defaultFS", "file:///");
            conf.setBoolean("fs.file.impl.disable.cache", true);

            org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(file.toUri());
            InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);

            try (ParquetReader<GenericRecord> reader = 
                    AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
                GenericRecord firstRecord = reader.read();
                if (firstRecord == null) {
                    return false;
                }

                org.apache.avro.Schema schema = firstRecord.getSchema();
                // Check for Yellow taxi specific field
                return schema.getField("tpep_pickup_datetime") != null &&
                       schema.getField("tpep_dropoff_datetime") != null &&
                       schema.getField("lpep_pickup_datetime") == null; // Ensure it's not Green
            }
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Processes a Green taxi Parquet file and indexes to OpenSearch.
     */
    private void processGreenTaxiFile(Path file) throws IOException, GreenReader.SchemaValidationException {
        logger.info("Processing Green taxi file: {}", file.getFileName());

        try (Stream<GreenTaxi> stream = greenReader.read(file)) {
            int count = 0;
            var iterator = stream.iterator();
            java.util.List<Map<String, Object>> batch = new java.util.ArrayList<>();

            while (iterator.hasNext()) {
                GreenTaxi taxi = iterator.next();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> doc = objectMapper.convertValue(taxi, Map.class);
                    batch.add(doc);
                    count++;

                    // Index in batches
                    if (batch.size() >= BATCH_SIZE) {
                        openSearchService.bulkIndex("greentaxi", batch);
                        batch.clear();
                        logger.debug("Indexed batch of {} Green taxi records", BATCH_SIZE);
                    }
                } catch (Exception e) {
                    logger.error("Error serializing GreenTaxi to JSON (record #{}, vendorID: {}, pickup: {}, dropoff: {}): {}", 
                        count + 1,
                        taxi != null ? taxi.vendorID() : "null",
                        taxi != null && taxi.lpepPickupDateTime() != null ? taxi.lpepPickupDateTime() : "null",
                        taxi != null && taxi.lpepDropoffDateTime() != null ? taxi.lpepDropoffDateTime() : "null",
                        e.getMessage(), e);
                    // Continue processing other records instead of stopping
                }
            }

            // Index remaining records
            if (!batch.isEmpty()) {
                openSearchService.bulkIndex("greentaxi", batch);
            }

            // Update metrics
            greenTaxiRecordsCounter.increment(count);
            logger.info("Indexed {} Green taxi records from file: {}", count, file.getFileName());
        }
    }

    /**
     * Processes a Yellow taxi Parquet file and indexes to OpenSearch.
     */
    private void processYellowTaxiFile(Path file) throws IOException, YellowReader.SchemaValidationException {
        logger.info("Processing Yellow taxi file: {}", file.getFileName());

        try (Stream<YellowTaxi> stream = yellowReader.read(file)) {
            int count = 0;
            var iterator = stream.iterator();
            java.util.List<Map<String, Object>> batch = new java.util.ArrayList<>();

            while (iterator.hasNext()) {
                YellowTaxi taxi = iterator.next();
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> doc = objectMapper.convertValue(taxi, Map.class);
                    batch.add(doc);
                    count++;

                    // Index in batches
                    if (batch.size() >= BATCH_SIZE) {
                        openSearchService.bulkIndex("yellowtaxi", batch);
                        batch.clear();
                        logger.debug("Indexed batch of {} Yellow taxi records", BATCH_SIZE);
                    }
                } catch (Exception e) {
                    logger.error("Error serializing YellowTaxi to JSON (record #{}, vendorID: {}, pickup: {}, dropoff: {}): {}", 
                        count + 1,
                        taxi != null ? taxi.vendorID() : "null",
                        taxi != null && taxi.tpepPickupDateTime() != null ? taxi.tpepPickupDateTime() : "null",
                        taxi != null && taxi.tpepDropoffDateTime() != null ? taxi.tpepDropoffDateTime() : "null",
                        e.getMessage(), e);
                    // Continue processing other records instead of stopping
                }
            }

            // Index remaining records
            if (!batch.isEmpty()) {
                openSearchService.bulkIndex("yellowtaxi", batch);
            }

            // Update metrics
            yellowTaxiRecordsCounter.increment(count);
            logger.info("Indexed {} Yellow taxi records from file: {}", count, file.getFileName());
        }
    }

    /**
     * Moves a file to the error directory with a reason suffix.
     */
    private void moveToErrorDirectory(Path file, Path errorDir, String reason) throws IOException {
        String fileName = file.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        String errorFileName = baseName + "_" + reason + extension;

        Path errorFile = errorDir.resolve(errorFileName);
        Files.move(file, errorFile, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Moved file {} to error directory as {}", fileName, errorFileName);
    }

    /**
     * Enum for schema types.
     */
    private enum SchemaType {
        GREEN,
        YELLOW,
        UNKNOWN
    }
}

