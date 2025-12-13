package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Unit tests for the ParquetFileDirectoryMonitor service.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ParquetFileDirectoryMonitor Tests")
class ParquetFileDirectoryMonitorTest {

    @Mock
    private ParquetToDatabaseService parquetToDatabaseService;

    private MeterRegistry meterRegistry;
    private ParquetFileDirectoryMonitor monitor;

    @TempDir
    Path tempDir;

    private Path inputDir;
    private Path errorDir;
    private Path processedDir;

    @BeforeEach
    void setUp() throws Exception {
        inputDir = tempDir.resolve("input");
        errorDir = tempDir.resolve("error");
        processedDir = tempDir.resolve("processed");
        
        Files.createDirectories(inputDir);
        Files.createDirectories(errorDir);
        Files.createDirectories(processedDir);

        // Set system properties for directories
        System.setProperty("taxi.monitor.input.dir", inputDir.toString());
        System.setProperty("taxi.monitor.error.dir", errorDir.toString());
        System.setProperty("taxi.monitor.processed.dir", processedDir.toString());

        // Use a real SimpleMeterRegistry for testing
        meterRegistry = new SimpleMeterRegistry();

        // Create monitor instance and inject dependencies using reflection
        monitor = new ParquetFileDirectoryMonitor();
        
        java.lang.reflect.Field parquetServiceField = ParquetFileDirectoryMonitor.class.getDeclaredField("parquetToDatabaseService");
        parquetServiceField.setAccessible(true);
        parquetServiceField.set(monitor, parquetToDatabaseService);

        java.lang.reflect.Field meterRegistryField = ParquetFileDirectoryMonitor.class.getDeclaredField("meterRegistry");
        meterRegistryField.setAccessible(true);
        meterRegistryField.set(monitor, meterRegistry);

        // Initialize WatchService and ExecutorService (normally done in @PostConstruct init())
        java.lang.reflect.Field watchServiceField = ParquetFileDirectoryMonitor.class.getDeclaredField("watchService");
        watchServiceField.setAccessible(true);
        watchServiceField.set(monitor, java.nio.file.FileSystems.getDefault().newWatchService());

        java.lang.reflect.Field executorServiceField = ParquetFileDirectoryMonitor.class.getDeclaredField("executorService");
        executorServiceField.setAccessible(true);
        executorServiceField.set(monitor, java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "ParquetFileDirectoryMonitor-Test");
            t.setDaemon(true);
            return t;
        }));

        // Initialize metrics
        monitor.initMetrics();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Stop monitoring if it's running
        if (monitor != null) {
            monitor.stopMonitoring();
            monitor.cleanup();
        }

        // Clean up system properties
        System.clearProperty("taxi.monitor.input.dir");
        System.clearProperty("taxi.monitor.error.dir");
        System.clearProperty("taxi.monitor.processed.dir");
    }

    /**
     * Helper method to find a test Parquet file in common locations.
     */
    private Path findTestParquetFile(String fileName) {
        Path[] possiblePaths = {
            Paths.get(fileName),
            Paths.get("data", fileName),
            Paths.get("../", fileName),
            Paths.get("src/test/resources", fileName),
            Paths.get("src/main/resources", fileName)
        };

        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    @Test
    @DisplayName("Should detect and process green taxi parquet file")
    void testProcessGreenTaxiFile() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Mock ParquetToDatabaseService to return record count
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenReturn(100L);

        // Process existing files (simulating what happens after monitoring starts)
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called with correct parameters
        ArgumentCaptor<Path> pathCaptor = ArgumentCaptor.forClass(Path.class);
        ArgumentCaptor<String> tableNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Boolean> dropIfExistsCaptor = ArgumentCaptor.forClass(Boolean.class);

        verify(parquetToDatabaseService, times(1)).processParquetFile(
            pathCaptor.capture(),
            tableNameCaptor.capture(),
            dropIfExistsCaptor.capture()
        );

        // Verify the captured values
        Path capturedPath = pathCaptor.getValue();
        assertTrue(capturedPath.toString().contains("green_tripdata_2025_01.parquet"));
        
        String capturedTableName = tableNameCaptor.getValue();
        assertTrue(capturedTableName.startsWith("greentaxi_"), 
            "Table name should start with 'greentaxi_'");
        assertTrue(capturedTableName.contains("green_tripdata_2025_01"), 
            "Table name should contain file name");
        
        assertEquals(false, dropIfExistsCaptor.getValue(), 
            "dropIfExists should be false");

        // Verify metrics were incremented
        Counter greenFilesCounter = meterRegistry.counter("parquet.monitor.green.files");
        Counter greenRecordsCounter = meterRegistry.counter("parquet.monitor.green.records");
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1");
        assertEquals(100.0, greenRecordsCounter.count(), "Green records counter should be 100");
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
    }

    @Test
    @DisplayName("Should detect and process yellow taxi parquet file")
    void testProcessYellowTaxiFile() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("yellow_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Mock ParquetToDatabaseService to return record count
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenReturn(200L);

        // Process existing files
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called
        ArgumentCaptor<String> tableNameCaptor = ArgumentCaptor.forClass(String.class);
        verify(parquetToDatabaseService, times(1)).processParquetFile(
            any(Path.class),
            tableNameCaptor.capture(),
            anyBoolean()
        );

        // Verify table name
        String capturedTableName = tableNameCaptor.getValue();
        assertTrue(capturedTableName.startsWith("yellowtaxi_"), 
            "Table name should start with 'yellowtaxi_'");

        // Verify metrics were incremented
        Counter yellowFilesCounter = meterRegistry.counter("parquet.monitor.yellow.files");
        Counter yellowRecordsCounter = meterRegistry.counter("parquet.monitor.yellow.records");
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        
        assertEquals(1.0, yellowFilesCounter.count(), "Yellow files counter should be 1");
        assertEquals(200.0, yellowRecordsCounter.count(), "Yellow records counter should be 200");
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
    }

    @Test
    @DisplayName("Should move non-parquet file to error directory")
    void testProcessNonParquetFile() throws Exception {
        // Create a non-parquet file
        Path txtFile = inputDir.resolve("not_parquet.txt");
        Files.write(txtFile, "This is not a parquet file".getBytes());

        // Process the file directly via processFileAsync (which internally calls processFile)
        // Note: processExistingFiles filters for .parquet files, so we test processFile directly
        java.lang.reflect.Method processFileAsyncMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processFileAsync", Path.class);
        processFileAsyncMethod.setAccessible(true);
        processFileAsyncMethod.invoke(monitor, txtFile);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was NOT called
        verify(parquetToDatabaseService, never()).processParquetFile(
            any(Path.class), anyString(), anyBoolean());

        // Verify file was moved to error directory with "not_parquet" suffix
        assertFalse(Files.exists(txtFile), "Original file should not exist");
        
        // Check error directory
        long errorFileCount = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("not_parquet"))
            .count();
        
        assertTrue(errorFileCount > 0, "File should be moved to error directory");

        // Verify metrics
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("parquet.monitor.files.errored");
        
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
        assertEquals(1.0, erroredFilesCounter.count(), "Errored files counter should be 1");
    }

    @Test
    @DisplayName("Should handle processing errors and move file to error directory")
    void testProcessingError() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Mock ParquetToDatabaseService to throw an exception
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenThrow(new SQLException("Database connection failed"));

        // Process existing files
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called
        verify(parquetToDatabaseService, times(1)).processParquetFile(
            any(Path.class), anyString(), anyBoolean());

        // Verify file was moved to error directory with "processing_error" suffix
        assertFalse(Files.exists(inputFile), "Original file should not exist");
        
        long errorFileCount = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("green_tripdata_2025_01"))
            .filter(path -> path.getFileName().toString().contains("processing_error"))
            .count();
        assertTrue(errorFileCount > 0, "File should be moved to error directory with processing_error suffix");

        // Verify metrics
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        Counter erroredFilesCounter = meterRegistry.counter("parquet.monitor.files.errored");
        Counter processingErrorsCounter = meterRegistry.counter("parquet.monitor.processing.errors");
        
        assertEquals(1.0, filesProcessedCounter.count(), "Files processed counter should be 1");
        assertEquals(1.0, erroredFilesCounter.count(), "Errored files counter should be 1");
        assertEquals(1.0, processingErrorsCounter.count(), "Processing errors counter should be 1");
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

        // Mock ParquetToDatabaseService to return record count
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenReturn(100L);

        // Process existing files twice
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);
        
        // Wait for processing
        Thread.sleep(2000);
        
        // Process again - should not reprocess
        processExistingFilesMethod.invoke(monitor, inputDir);
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called only once
        verify(parquetToDatabaseService, times(1)).processParquetFile(
            any(Path.class), anyString(), anyBoolean());

        // Verify metrics were incremented only once
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        assertEquals(1.0, filesProcessedCounter.count(), 
            "Files processed counter should be 1 (not reprocessed)");
    }

    @Test
    @DisplayName("Should process multiple files correctly")
    void testProcessMultipleFiles() throws Exception {
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

        // Mock ParquetToDatabaseService
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenReturn(100L);

        // Process existing files
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called twice
        verify(parquetToDatabaseService, times(2)).processParquetFile(
            any(Path.class), anyString(), anyBoolean());

        // Verify metrics for both types
        Counter greenFilesCounter = meterRegistry.counter("parquet.monitor.green.files");
        Counter yellowFilesCounter = meterRegistry.counter("parquet.monitor.yellow.files");
        Counter filesProcessedCounter = meterRegistry.counter("parquet.monitor.files.processed");
        
        assertEquals(1.0, greenFilesCounter.count(), "Green files counter should be 1");
        assertEquals(1.0, yellowFilesCounter.count(), "Yellow files counter should be 1");
        assertEquals(2.0, filesProcessedCounter.count(), "Files processed counter should be 2");
    }

    @Test
    @DisplayName("Should move successfully processed file to processed directory")
    void testMoveToProcessedDirectory() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Mock ParquetToDatabaseService to return record count
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenReturn(100L);

        // Process existing files
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify file was moved to processed directory
        assertFalse(Files.exists(inputFile), "Original file should not exist in input directory");
        
        Path processedFile = processedDir.resolve("green_tripdata_2025_01.parquet");
        assertTrue(Files.exists(processedFile), "File should exist in processed directory");
    }

    @Test
    @DisplayName("Should handle IOException during file processing")
    void testIOExceptionDuringProcessing() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        // Copy test file to input directory
        Path inputFile = inputDir.resolve("green_tripdata_2025_01.parquet");
        Files.copy(testFile, inputFile);

        // Mock ParquetToDatabaseService to throw IOException
        when(parquetToDatabaseService.processParquetFile(any(Path.class), anyString(), anyBoolean()))
            .thenThrow(new IOException("File read error"));

        // Process existing files
        java.lang.reflect.Method processExistingFilesMethod = 
            ParquetFileDirectoryMonitor.class.getDeclaredMethod("processExistingFiles", Path.class);
        processExistingFilesMethod.setAccessible(true);
        processExistingFilesMethod.invoke(monitor, inputDir);

        // Wait for async processing to complete
        Thread.sleep(2000);

        // Verify ParquetToDatabaseService was called
        verify(parquetToDatabaseService, times(1)).processParquetFile(
            any(Path.class), anyString(), anyBoolean());

        // Verify file was moved to error directory
        assertFalse(Files.exists(inputFile), "Original file should not exist");
        
        long errorFileCount = Files.list(errorDir)
            .filter(path -> path.getFileName().toString().contains("green_tripdata_2025_01"))
            .filter(path -> path.getFileName().toString().contains("processing_error"))
            .count();
        assertTrue(errorFileCount > 0, "File should be moved to error directory");

        // Verify error metrics
        Counter processingErrorsCounter = meterRegistry.counter("parquet.monitor.processing.errors");
        assertEquals(1.0, processingErrorsCounter.count(), 
            "Processing errors counter should be 1");
    }

    @Test
    @DisplayName("Should stop monitoring correctly")
    void testStopMonitoring() throws Exception {
        // Set running flag to true (simulating monitoring started)
        java.lang.reflect.Field runningField = ParquetFileDirectoryMonitor.class.getDeclaredField("running");
        runningField.setAccessible(true);
        runningField.setBoolean(monitor, true);

        // Verify monitoring appears to be running
        assertTrue(runningField.getBoolean(monitor), "Monitoring should be running");

        // Stop monitoring
        monitor.stopMonitoring();

        // Verify monitoring is stopped
        assertFalse(runningField.getBoolean(monitor), "Monitoring should be stopped");

        // Calling stop again should be safe
        monitor.stopMonitoring();
        assertFalse(runningField.getBoolean(monitor), "Monitoring should still be stopped");
    }
}

