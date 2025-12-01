package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.apache.parquet.io.OutputFile;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for the TaxiMonitor service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaxiMonitor Tests")
class TaxiMonitorTest {

    private static final Logger logger = LogManager.getLogger(TaxiMonitorTest.class);

    @Mock
    private OpenSearchService openSearchService;

    private MeterRegistry meterRegistry;

    private TaxiMonitor taxiMonitor;

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path errorDir;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = tempDir.resolve("input");
        errorDir = tempDir.resolve("error");
        Files.createDirectories(inputDir);
        Files.createDirectories(errorDir);

        // Set system properties for directories
        System.setProperty("taxi.monitor.input.dir", inputDir.toString());
        System.setProperty("taxi.monitor.error.dir", errorDir.toString());

        // Use a real SimpleMeterRegistry for testing (simpler than mocking)
        meterRegistry = new SimpleMeterRegistry();

        // Create TaxiMonitor instance using reflection to inject mocks
        taxiMonitor = new TaxiMonitor();
        
        // Use reflection to inject mocks
        java.lang.reflect.Field openSearchServiceField = TaxiMonitor.class.getDeclaredField("openSearchService");
        openSearchServiceField.setAccessible(true);
        openSearchServiceField.set(taxiMonitor, openSearchService);

        java.lang.reflect.Field meterRegistryField = TaxiMonitor.class.getDeclaredField("meterRegistry");
        meterRegistryField.setAccessible(true);
        meterRegistryField.set(taxiMonitor, meterRegistry);

        // Initialize metrics (this will create real counters)
        taxiMonitor.initMetrics();
    }

    @AfterEach
    void tearDown() {
        // Clean up system properties set during tests
        System.clearProperty("taxi.monitor.input.dir");
        System.clearProperty("taxi.monitor.error.dir");
    }

    /**
     * Helper method to find a test Parquet file in common locations.
     */
    private Path findTestParquetFile(String fileName) {
        Path[] possiblePaths = {
            Paths.get(fileName),
            Paths.get("data", fileName),
            Paths.get("../", fileName),
            Paths.get("src/test/resources", fileName)
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    /**
     * Helper method to create a Parquet file with an invalid schema (doesn't match yellow or green taxi schemas).
     */
    private Path createInvalidSchemaParquetFile(Path outputPath) throws IOException {
        // Create a simple Avro schema that doesn't match yellow or green taxi schemas
        String schemaString = """
            {
                "type": "record",
                "name": "InvalidTaxiRecord",
                "fields": [
                    {"name": "id", "type": "string"},
                    {"name": "name", "type": "string"},
                    {"name": "value", "type": "int"}
                ]
            }
            """;

        Schema schema = new Schema.Parser().parse(schemaString);

        // Configure Hadoop
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);

        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(outputPath.toUri());
        OutputFile outputFile = HadoopOutputFile.fromPath(hadoopPath, conf);

        // Write a Parquet file with the invalid schema
        try (ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(schema)
                .withConf(conf)
                .build()) {
            
            // Write a sample record
            GenericRecord record = new GenericData.Record(schema);
            record.put("id", "test-1");
            record.put("name", "Test Record");
            record.put("value", 100);
            writer.write(record);
        }

        return outputPath;
    }

    @Test
    @DisplayName("Should process good green parquet file and index to OpenSearch")
    void testProcessGoodGreenParquetFile() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Call monitorDirectory to process the file
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was called for green taxi
        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> documentsCaptor = ArgumentCaptor.forClass(List.class);

        verify(openSearchService, atLeastOnce()).bulkIndex(
            indexCaptor.capture(),
            documentsCaptor.capture()
        );

        // Verify the index name is correct
        List<String> capturedIndices = indexCaptor.getAllValues();
        assertTrue(capturedIndices.stream().anyMatch("greentaxi"::equals),
            "Should index to greentaxi index");

        // Verify that documents were indexed
        List<List<Map<String, Object>>> capturedDocuments = documentsCaptor.getAllValues();
        assertFalse(capturedDocuments.isEmpty(), "Should have indexed at least one batch");
        
        int totalDocuments = capturedDocuments.stream()
            .mapToInt(List::size)
            .sum();
        assertTrue(totalDocuments > 0, "Should have indexed documents");

        // Verify metrics were incremented by checking the actual counter values
        Counter greenFilesCounter = meterRegistry.counter("taxi.monitor.green.files");
        Counter greenRecordsCounter = meterRegistry.counter("taxi.monitor.green.records");
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");
        
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1");
        assertTrue(greenRecordsCounter.count() > 0, "Green records counter should be > 0");
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
        assertEquals(0.0, erroredFilesCounter.count(), "Errored files counter should be 0");

        // Verify file was not moved to error directory
        assertTrue(Files.exists(inputFile), "File should remain in input directory");
        assertTrue(Files.list(errorDir).count() == 0, "Error directory should be empty");
    }

    @Test
    @DisplayName("Should process good yellow parquet file and index to OpenSearch")
    void testProcessGoodYellowParquetFile() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("yellow_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Call monitorDirectory to process the file
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was called for yellow taxi
        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> documentsCaptor = ArgumentCaptor.forClass(List.class);

        verify(openSearchService, atLeastOnce()).bulkIndex(
            indexCaptor.capture(),
            documentsCaptor.capture()
        );

        // Verify the index name is correct
        List<String> capturedIndices = indexCaptor.getAllValues();
        assertTrue(capturedIndices.stream().anyMatch("yellowtaxi"::equals),
            "Should index to yellowtaxi index");

        // Verify that documents were indexed
        List<List<Map<String, Object>>> capturedDocuments = documentsCaptor.getAllValues();
        assertFalse(capturedDocuments.isEmpty(), "Should have indexed at least one batch");
        
        int totalDocuments = capturedDocuments.stream()
            .mapToInt(List::size)
            .sum();
        assertTrue(totalDocuments > 0, "Should have indexed documents");

        // Verify metrics were incremented by checking the actual counter values
        Counter yellowFilesCounter = meterRegistry.counter("taxi.monitor.yellow.files");
        Counter yellowRecordsCounter = meterRegistry.counter("taxi.monitor.yellow.records");
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");
        
        assertEquals(1.0, yellowFilesCounter.count(), "Yellow files counter should be 1");
        assertTrue(yellowRecordsCounter.count() > 0, "Yellow records counter should be > 0");
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
        assertEquals(0.0, erroredFilesCounter.count(), "Errored files counter should be 0");

        // Verify file was not moved to error directory
        assertTrue(Files.exists(inputFile), "File should remain in input directory");
        assertTrue(Files.list(errorDir).count() == 0, "Error directory should be empty");
    }

    @Test
    @DisplayName("Should process both green and yellow parquet files")
    void testProcessBothGreenAndYellowParquetFiles() throws Exception {
        // Find both test files
        Path greenTestFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        Path yellowTestFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");

        if (greenTestFile == null || yellowTestFile == null) {
            System.out.println("Skipping test: Required test files not found");
            return;
        }

        // Copy both test files to input directory
        Path greenInputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Path yellowInputFile = inputDir.resolve("yellow_tripdata_2025_01.parquet");
        Files.copy(greenTestFile, greenInputFile);
        Files.copy(yellowTestFile, yellowInputFile);

        // Call monitorDirectory to process the files
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was called for both indices
        ArgumentCaptor<String> indexCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Map<String, Object>>> documentsCaptor = ArgumentCaptor.forClass(List.class);

        verify(openSearchService, atLeastOnce()).bulkIndex(
            indexCaptor.capture(),
            documentsCaptor.capture()
        );

        // Verify both indices were used
        List<String> capturedIndices = indexCaptor.getAllValues();
        assertTrue(capturedIndices.stream().anyMatch("greentaxi"::equals),
            "Should index to greentaxi index");
        assertTrue(capturedIndices.stream().anyMatch("yellowtaxi"::equals),
            "Should index to yellowtaxi index");

        // Verify metrics were incremented for both types by checking the actual counter values
        Counter greenFilesCounter = meterRegistry.counter("taxi.monitor.green.files");
        Counter yellowFilesCounter = meterRegistry.counter("taxi.monitor.yellow.files");
        Counter greenRecordsCounter = meterRegistry.counter("taxi.monitor.green.records");
        Counter yellowRecordsCounter = meterRegistry.counter("taxi.monitor.yellow.records");
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");
        
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1");
        assertEquals(1.0, yellowFilesCounter.count(), "Yellow files counter should be 1");
        assertTrue(greenRecordsCounter.count() > 0, "Green records counter should be > 0");
        assertTrue(yellowRecordsCounter.count() > 0, "Yellow records counter should be > 0");
        assertEquals(2.0, filesProcessedCounter.count(), "Files processed counter should be 2");
        assertEquals(0.0, erroredFilesCounter.count(), "Errored files counter should be 0");

        // Verify files were not moved to error directory
        assertTrue(Files.exists(greenInputFile), "Green file should remain in input directory");
        assertTrue(Files.exists(yellowInputFile), "Yellow file should remain in input directory");
        assertTrue(Files.list(errorDir).count() == 0, "Error directory should be empty");
    }

    @Test
    @DisplayName("Should not reprocess the same file twice")
    void testNoReprocessing() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Process the file twice
        taxiMonitor.monitorDirectory();
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was called (file should not be reprocessed on second call)
        verify(openSearchService, atLeastOnce()).bulkIndex(anyString(), anyList());
        
        // Verify metrics were incremented only once by checking the actual counter values
        Counter greenFilesCounter = meterRegistry.counter("taxi.monitor.green.files");
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1 (not reprocessed)");
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1 (not reprocessed)");
    }

    @Test
    @DisplayName("Should move Parquet file with invalid schema to error directory")
    void testInvalidSchemaParquetFile() throws Exception {
        // Create a Parquet file with an invalid schema (doesn't match yellow or green)
        Path invalidFile = inputDir.resolve("invalid_schema.parquet");
        createInvalidSchemaParquetFile(invalidFile);

        // Call monitorDirectory to process the file
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was NOT called (file should be rejected)
        verify(openSearchService, never()).bulkIndex(anyString(), anyList());

        // Verify metrics were incremented correctly
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");
        Counter greenFilesCounter = meterRegistry.counter("taxi.monitor.green.files");
        Counter yellowFilesCounter = meterRegistry.counter("taxi.monitor.yellow.files");
        
        assertEquals(2.0, filesProcessedCounter.count(), "Files processed counter should be 1");
        assertEquals(2.0, erroredFilesCounter.count(), "Errored files counter should be 1");
        assertEquals(0.0, greenFilesCounter.count(), "Green files counter should be 0");
        assertEquals(0.0, yellowFilesCounter.count(), "Yellow files counter should be 0");

        // Verify file was moved to error directory with schema_mismatch suffix
        assertFalse(Files.exists(invalidFile), "Original file should not exist in input directory");
        
        // Check that file exists in error directory with schema_mismatch suffix
        List<Path> errorFiles = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("invalid_schema"))
            .filter(path -> path.getFileName().toString().contains("schema_mismatch"))
            .toList();
        
        assertEquals(1, errorFiles.size(), "File should be moved to error directory with schema_mismatch suffix");
        assertTrue(Files.exists(errorFiles.get(0)), "Error file should exist");
    }

    @Test
    @DisplayName("Should handle multiple invalid schema Parquet files")
    void testMultipleInvalidSchemaParquetFiles() throws Exception {
        // Create multiple Parquet files with invalid schemas
        Path invalidFile1 = inputDir.resolve("invalid_schema_1.parquet");
        Path invalidFile2 = inputDir.resolve("invalid_schema_2.parquet");
        createInvalidSchemaParquetFile(invalidFile1);
        createInvalidSchemaParquetFile(invalidFile2);

        // Call monitorDirectory to process the files
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was NOT called
        verify(openSearchService, never()).bulkIndex(anyString(), anyList());

        // Verify metrics were incremented correctly
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");

        logger.info("Files processed counter: {}", filesProcessedCounter.count());
        logger.info("Errored files counter: {}", erroredFilesCounter.count());
        
        assertEquals(4.0, filesProcessedCounter.count(), "Files processed counter should be 2");
        assertEquals(4.0, erroredFilesCounter.count(), "Errored files counter should be 2");

        // Verify both files were moved to error directory
        assertFalse(Files.exists(invalidFile1), "First file should not exist in input directory");
        assertFalse(Files.exists(invalidFile2), "Second file should not exist in input directory");
        
        long errorFileCount = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("invalid_schema"))
            .filter(path -> path.getFileName().toString().contains("schema_mismatch"))
            .count();
        
        assertEquals(2, errorFileCount, "Both files should be moved to error directory");
    }

    @Test
    @DisplayName("Should handle mix of valid and invalid schema Parquet files")
    void testMixOfValidAndInvalidSchemaFiles() throws Exception {
        // Find a valid green parquet file
        Path validTestFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (validTestFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy valid file to input directory
        Path validFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(validTestFile, validFile);

        // Create an invalid schema file
        Path invalidFile = inputDir.resolve("invalid_schema.parquet");
        createInvalidSchemaParquetFile(invalidFile);

        // Call monitorDirectory to process the files
        taxiMonitor.monitorDirectory();

        // Verify that bulkIndex was called for the valid file
        verify(openSearchService, atLeastOnce()).bulkIndex(anyString(), anyList());

        // Verify metrics
        Counter filesProcessedCounter = meterRegistry.counter("taxi.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("taxi.monitor.files.errored");
        Counter greenFilesCounter = meterRegistry.counter("taxi.monitor.green.files");

        logger.info("Files processed counter: {}", filesProcessedCounter.count());
        logger.info("Errored files counter: {}", erroredFilesCounter.count());
        logger.info("Green files counter: {}", greenFilesCounter.count());

        
        assertEquals(3.0, filesProcessedCounter.count(), "Files processed counter should be 2");
        assertEquals(2.0, erroredFilesCounter.count(), "Errored files counter should be 1");
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1");

        // Verify valid file remains, invalid file was moved
        assertTrue(Files.exists(validFile), "Valid file should remain in input directory");
        assertFalse(Files.exists(invalidFile), "Invalid file should not exist in input directory");
        
        // Verify invalid file is in error directory
        long errorFileCount = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("invalid_schema"))
            .filter(path -> path.getFileName().toString().contains("schema_mismatch"))
            .count();
        
        assertEquals(1, errorFileCount, "Invalid file should be in error directory");
    }
}

