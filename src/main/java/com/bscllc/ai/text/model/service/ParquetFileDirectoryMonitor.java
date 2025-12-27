package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.io.InputFile;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import com.bscllc.ai.text.model.TaxiParquetSchemas;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Service that monitors a directory for Green and Yellow taxi Parquet files in real-time
 * and processes them using ParquetToDatabaseService.
 */
@ApplicationScoped
public class ParquetFileDirectoryMonitor {

    private static final Logger logger = LogManager.getLogger(ParquetFileDirectoryMonitor.class);
    private static final String PARQUET_EXTENSION = ".parquet";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    @Inject
    ParquetToDatabaseService parquetToDatabaseService;
    
    @Inject
    MeterRegistry meterRegistry;
    
    @ConfigProperty(name = "parquet.monitor.enabled", defaultValue = "true")
    boolean enabled;
    
    @ConfigProperty(name = "parquet.monitor.input.dir", defaultValue = "./data/parquet-input")
    String inputDir;
    
    @ConfigProperty(name = "parquet.monitor.error.dir", defaultValue = "./data/parquet-error")
    String errorDir;
    
    @ConfigProperty(name = "parquet.monitor.processed.dir", defaultValue = "./data/parquet-processed")
    String processedDir;
    
    @ConfigProperty(name = "parquet.monitor.batch.size", defaultValue = "10")
    int batchSize;
    
    @ConfigProperty(name = "parquet.monitor.batch.timer.seconds", defaultValue = "15")
    int batchTimerSeconds;
    
    private WatchService watchService;
    private ExecutorService executorService;  // For monitoring loop
    private ExecutorService fileProcessingExecutorService;  // For file processing tasks
    private boolean running = false;
    
    // Track processed files to avoid reprocessing
    private final Set<String> processedFiles = ConcurrentHashMap.newKeySet();
    
    // Track files currently being processed
    private final Set<String> processingFiles = ConcurrentHashMap.newKeySet();
    
    // Queue for files waiting to be processed in batches
    private final java.util.Queue<Path> fileQueue = new java.util.concurrent.ConcurrentLinkedQueue<>();
    
    // Last batch processing time
    private volatile long lastBatchProcessTime = System.currentTimeMillis();
    
    // Flag to prevent concurrent batch processing
    private volatile boolean batchProcessingInProgress = false;
    
    // Micrometer metrics
    private Counter filesProcessedCounter;
    private Counter yellowTaxiFilesCounter;
    private Counter yellowTaxiRecordsCounter;
    private Counter greenTaxiFilesCounter;
    private Counter greenTaxiRecordsCounter;
    private Counter erroredFilesCounter;
    private Counter processingErrorsCounter;

    @PostConstruct
    void init() {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            // Single-threaded executor for monitoring loop
            executorService = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ParquetFileDirectoryMonitor-WatchService");
                t.setDaemon(true);
                return t;
            });
            // Thread pool executor for file processing (allows concurrent file processing)
            fileProcessingExecutorService = Executors.newFixedThreadPool(5, r -> {
                Thread t = new Thread(r, "ParquetFileDirectoryMonitor-FileProcessor");
                t.setDaemon(true);
                return t;
            });
            
            logger.info("ParquetFileDirectoryMonitor initialized (watchService and executorService created)");
        } catch (IOException e) {
            logger.error("Failed to initialize WatchService", e);
            throw new RuntimeException("Failed to initialize ParquetFileDirectoryMonitor", e);
        }
    }
    
    /**
     * Starts monitoring when the application starts.
     * This ensures the service starts even in dev mode.
     */
    void onStart(@Observes StartupEvent ev) {
        // Initialize metrics after application startup when MeterRegistry is guaranteed to be available
        initMetrics();
        
        if (!enabled) {
            logger.info("ParquetFileDirectoryMonitor is disabled. Set parquet.monitor.enabled=true to enable.");
            return;
        }
        
        logger.info("ParquetFileDirectoryMonitor is enabled. Monitoring directory for files to process to PostgreSQL.");
        logger.info("Input directory: {}", inputDir);
        logger.info("Error directory: {}", errorDir);
        logger.info("Processed directory: {}", processedDir);
        
        // Start monitoring after application startup
        // This ensures the bean is fully initialized before starting
        if (watchService != null && executorService != null) {
            startMonitoring();
            logger.info("ParquetFileDirectoryMonitor started and monitoring for files to process to PostgreSQL.");
        } else {
            logger.error("Cannot start monitoring: watchService or executorService is null");
        }
    }

    void initMetrics() {
        if (meterRegistry == null) {
            logger.warn("MeterRegistry is not available. Metrics will not be collected.");
            return;
        }
        
        try {
            filesProcessedCounter = Counter.builder("parquet.monitor.files.processed")
                    .description("Total number of parquet files processed by directory monitor")
                    .register(meterRegistry);
            
            yellowTaxiFilesCounter = Counter.builder("parquet.monitor.yellow.files")
                    .description("Number of yellow taxi files processed by directory monitor")
                    .register(meterRegistry);
            
            yellowTaxiRecordsCounter = Counter.builder("parquet.monitor.yellow.records")
                    .description("Number of yellow taxi records processed by directory monitor")
                    .register(meterRegistry);
            
            greenTaxiFilesCounter = Counter.builder("parquet.monitor.green.files")
                    .description("Number of green taxi files processed by directory monitor")
                    .register(meterRegistry);
            
            greenTaxiRecordsCounter = Counter.builder("parquet.monitor.green.records")
                    .description("Number of green taxi records processed by directory monitor")
                    .register(meterRegistry);
            
            erroredFilesCounter = Counter.builder("parquet.monitor.files.errored")
                    .description("Number of files that failed processing by directory monitor")
                    .register(meterRegistry);
            
            processingErrorsCounter = Counter.builder("parquet.monitor.processing.errors")
                    .description("Number of processing errors in directory monitor")
                    .register(meterRegistry);
            
            logger.info("ParquetFileDirectoryMonitor metrics initialized successfully");
        } catch (Exception e) {
            logger.error("Failed to initialize metrics", e);
        }
    }

    /**
     * Starts monitoring the input directory for new Parquet files.
     */
    public void startMonitoring() {
        if (running) {
            logger.warn("Directory database monitoring is already running");
            return;
        }
        
        Path inputPath = Paths.get(inputDir);
        
        logger.info("Starting directory database monitoring for: {}", inputPath.toAbsolutePath().normalize());
        logger.info("Error databasedirectory: {}", Paths.get(errorDir).toAbsolutePath().normalize());
        logger.info("Processed database directory: {}", Paths.get(processedDir).toAbsolutePath().normalize());
        
        try {
            // Ensure directory exists
            Files.createDirectories(inputPath);
            
            // Register watch service for CREATE events
            inputPath.register(watchService, 
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);
            
            running = true;
            
            // Start monitoring in background thread
            executorService.submit(this::monitorLoop);
            
            // Process any existing files in the directory (add to queue)
            processExistingFiles(inputPath);
            
            // Start with initial batch processing
            if (!fileQueue.isEmpty()) {
                logger.info("Initial queue has {} files, processing first batch", fileQueue.size());
                processBatch();
            }
            
            logger.info("Directory monitoring started successfully");
        } catch (IOException e) {
            logger.error("Failed to start directory monitoring", e);
            running = false;
            throw new RuntimeException("Failed to start directory monitoring", e);
        }
    }

    /**
     * Processes any existing files in the input directory that haven't been processed yet.
     * Adds them to the queue for batch processing.
     */
    private void processExistingFiles(Path inputPath) {
        try {
            Files.list(inputPath)
                .filter(Files::isRegularFile)
                .filter(file -> file.toString().toLowerCase().endsWith(PARQUET_EXTENSION))
                .filter(file -> !processedFiles.contains(file.toString()) && 
                                !processingFiles.contains(file.toString()))
                .forEach(file -> fileQueue.offer(file));
            
            logger.info("Added {} existing files to processing queue", fileQueue.size());
        } catch (IOException e) {
            logger.error("Error processing existing files in directory: {}", inputPath, e);
        }
    }
    
    /**
     * Scheduled task that processes batches on a timer.
     * This ensures data is committed periodically even if batch size isn't reached.
     * Uses a configurable delay from parquet.monitor.batch.timer.seconds property.
     * 
     * This method runs every 10 seconds and checks if the timer has elapsed.
     * When the timer expires, it calls processBatch() to process all queued files.
     */
    @Scheduled(every = "10s", delay = 5)
    void processBatchOnTimer() {
        if (!enabled || !running) {
            logger.debug("Timer check skipped: enabled={}, running={}", enabled, running);
            return;
        }
        
        if (batchProcessingInProgress) {
            logger.debug("Timer check skipped: batch processing already in progress");
            return;
        }
        
        if (fileQueue.isEmpty()) {
            logger.debug("Timer check: queue is empty, nothing to process");
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long timeSinceLastBatch = (currentTime - lastBatchProcessTime) / 1000;
        
        logger.debug("Timer check: {}s since last batch, {} files in queue, batch timer: {}s", 
            timeSinceLastBatch, fileQueue.size(), batchTimerSeconds);
        
        // Process batch if queue has files and timer has elapsed
        // This is where queued files (from lines 410-424) get processed when the timer expires
        if (timeSinceLastBatch >= batchTimerSeconds) {
            logger.info("Timer triggered batch processing ({}s >= {}s elapsed, {} files in queue). Calling processBatch()...", 
                timeSinceLastBatch, batchTimerSeconds, fileQueue.size());
            processBatch();  // This processes the files that were queued in monitorLoop()
        } else {
            logger.debug("Timer not yet elapsed: {}s < {}s", timeSinceLastBatch, batchTimerSeconds);
        }
    }
    
    /**
     * Processes a batch of files from the queue.
     * Processes up to batchSize files or all files if queue is smaller than batchSize.
     * Synchronized to prevent concurrent batch processing.
     */
    private synchronized void processBatch() {
        // Prevent concurrent batch processing
        if (batchProcessingInProgress) {
            logger.debug("Batch processing already in progress, skipping");
            return;
        }
        
        if (fileQueue.isEmpty()) {
            logger.debug("processBatch called but queue is empty");
            return;
        }
        
        batchProcessingInProgress = true;
        lastBatchProcessTime = System.currentTimeMillis();
        
        try {
            List<Path> batch = new ArrayList<>();
            int skippedCount = 0;
            
            // Collect up to batchSize files from queue
            while (batch.size() < batchSize && !fileQueue.isEmpty()) {
                Path file = fileQueue.poll();
                if (file != null) {
                    String filePath = file.toString();
                    // Skip if already processed or currently being processed
                    if (processedFiles.contains(filePath)) {
                        logger.debug("Skipping file {} - already processed", file.getFileName());
                        skippedCount++;
                        continue;
                    }
                    if (processingFiles.contains(filePath)) {
                        logger.debug("Skipping file {} - currently being processed", file.getFileName());
                        skippedCount++;
                        continue;
                    }
                    batch.add(file);
                    logger.debug("Added file {} to batch (batch size: {}/{})", file.getFileName(), batch.size(), batchSize);
                }
            }
            
            if (batch.isEmpty()) {
                logger.info("No files to process in batch (skipped: {} files already processed/processing)", skippedCount);
                batchProcessingInProgress = false;
                return;
            }
            
            logger.info("Processing batch of {} files (batch size: {}, timer: {}s, skipped: {})", 
                batch.size(), batchSize, batchTimerSeconds, skippedCount);
            
            // Process each file in the batch asynchronously
            // Note: Files are processed concurrently, each with its own database connection
            // Each file commits independently, which is fine for this use case
            for (Path file : batch) {
                logger.info("Submitting file {} for async processing", file.getFileName());
                processFileAsync(file);
            }
            
            logger.info("Batch processing started for {} files", batch.size());
        } catch (Exception e) {
            logger.error("Error during batch processing", e);
        } finally {
            // Reset flag immediately - files are processed asynchronously so this is safe
            // The flag just prevents multiple batches from being extracted from the queue simultaneously
            batchProcessingInProgress = false;
            logger.debug("Batch processing flag reset");
        }
    }

    /**
     * Main monitoring loop that watches for file system events.
     */
    private void monitorLoop() {
        logger.info("File monitoring loop started");
        
        while (running) {
            try {
                WatchKey key = watchService.poll(1, TimeUnit.SECONDS);
                
                if (key == null) {
                    continue;
                }
                
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        logger.warn("WatchService overflow occurred");
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    Path fileName = ev.context();
                    Path dir = (Path) key.watchable();
                    Path fullPath = dir.resolve(fileName);
                    
                    // Only process .parquet files
                    if (fileName.toString().toLowerCase().endsWith(PARQUET_EXTENSION)) {
                        logger.info("Detected new parquet file: {}", fileName);
                        
                        // Check if file is already processed or being processed
                        String filePathString = fullPath.toString();
                        if (processedFiles.contains(filePathString)) {
                            logger.debug("File {} already processed, skipping", fileName);
                            continue;
                        }
                        if (processingFiles.contains(filePathString)) {
                            logger.debug("File {} currently being processed, skipping", fileName);
                            continue;
                        }
                        
                        // Add to queue for batch processing
                        boolean added = fileQueue.offer(fullPath);
                        if (added) {
                            logger.info("Added file to queue: {} (queue size: {}, batch size: {}, timer: {}s)", 
                                fileName, fileQueue.size(), batchSize, batchTimerSeconds);
                            
                            // Check if we should process batch immediately (size-based)
                            if (fileQueue.size() >= batchSize && !batchProcessingInProgress) {
                                logger.info("Batch size reached ({} >= {}), processing batch immediately", 
                                    fileQueue.size(), batchSize);
                                processBatch();
                            } else {
                                logger.info("File queued. Will be processed when batch size ({}) is reached or timer ({}s) elapses", 
                                    batchSize, batchTimerSeconds);
                            }
                        } else {
                            logger.warn("Failed to add file {} to queue (queue may be full)", fileName);
                        }
                    }
                }
                
                boolean valid = key.reset();
                if (!valid) {
                    logger.warn("WatchKey is no longer valid, stopping monitoring");
                    break;
                }
            } catch (InterruptedException e) {
                logger.info("Monitoring loop interrupted, shutting down");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Error in monitoring loop", e);
                if (processingErrorsCounter != null) {
                    processingErrorsCounter.increment();
                }
            }
        }
        
        logger.info("File monitoring loop stopped");
    }

    /**
     * Processes a file asynchronously in a separate thread.
     */
    private void processFileAsync(Path file) {
        String filePath = file.toString();
        
        // Skip if already processed or currently being processed
        if (processedFiles.contains(filePath) || processingFiles.contains(filePath)) {
            logger.debug("File {} already processed or being processed, skipping", file.getFileName());
            return;
        }
        
        // Add to processing set
        if (!processingFiles.add(filePath)) {
            logger.debug("File {} already being processed, skipping", file.getFileName());
            return;
        }
        
        logger.info("Processing file: {} in background thread", file.getFileName());
        
        // Process in background thread using the dedicated file processing executor
        // This ensures file processing doesn't block the monitoring loop
        // Note: Using submit() means exceptions are captured in the Future, but we handle them in processFile()
        fileProcessingExecutorService.submit(() -> {
            try {
                logger.debug("Starting to process file: {} in thread: {}", file.getFileName(), Thread.currentThread().getName());
                processFile(file);
                logger.debug("Completed processing file: {}", file.getFileName());
            } catch (Throwable e) {
                // This catch block should not normally execute since processFile() handles its own exceptions
                // But it's here as a safety net
                logger.error("Unexpected Throwable in async file processing wrapper for file: {}", file.getFileName(), e);
            } finally {
                processingFiles.remove(filePath);
                logger.debug("Removed file {} from processing set", filePath);
            }
        });
    }

    /**
     * Processes a single Parquet file.
     */
    private void processFile(Path file) {
        String fileName = file.getFileName().toString();
        String filePathString = file.toString();
        logger.info("Processing file: {} (full path: {})", fileName, filePathString);
        
        // Use the configured directories (already set via ConfigProperty)
        
        Path errorPath = Paths.get(errorDir);
        Path processedPath = Paths.get(processedDir);
        
        try {
            // Ensure directories exist
            Files.createDirectories(errorPath);
            Files.createDirectories(processedPath);
            
            // Check if file is a Parquet file
            if (!fileName.toLowerCase().endsWith(PARQUET_EXTENSION)) {
                logger.warn("File {} is not a Parquet file, moving to error directory", fileName);
                moveToErrorDirectory(file, errorPath, "not_parquet");
                if (filesProcessedCounter != null) filesProcessedCounter.increment();
                if (erroredFilesCounter != null) erroredFilesCounter.increment();
                processedFiles.add(file.toString());
                return;
            }
            
            // Determine schema type (green or yellow)
            SchemaType schemaType = detectSchemaType(file);
            
            if (schemaType == SchemaType.UNKNOWN) {
                logger.warn("File {} does not match Green or Yellow taxi schema, moving to error directory", fileName);
                moveToErrorDirectory(file, errorPath, "schema_mismatch");
                if (filesProcessedCounter != null) filesProcessedCounter.increment();
                if (erroredFilesCounter != null) erroredFilesCounter.increment();
                processedFiles.add(file.toString());
                return;
            }
            
            // Determine table name based on schema type
            // Use the schema name (YELLOW or GREEN) directly as the table name
            String tableName = schemaType == SchemaType.GREEN ? "GREEN" : "YELLOW";
            
            // Process file using ParquetToDatabaseService
            // Use dropIfExists=false to preserve existing data
            // Each file gets its own table to avoid table creation conflicts
            logger.info("Calling ParquetToDatabaseService.processParquetFile for file: {} with table: {}", fileName, tableName);
            long recordCount = 0;
            try {
                recordCount = parquetToDatabaseService.processParquetFile(file, tableName, false);
                logger.info("ParquetToDatabaseService.processParquetFile returned recordCount: {} for file: {}", recordCount, fileName);
            } catch (Exception e) {
                logger.error("Exception thrown by ParquetToDatabaseService.processParquetFile for file: {} table: {}", fileName, tableName, e);
                throw e; // Re-throw to be caught by outer catch blocks
            }
            
            logger.info("After processParquetFile call, recordCount: {} for file: {}", recordCount, fileName);
            
            // Update metrics (check for null to avoid NPE if metrics weren't initialized)
            if (filesProcessedCounter != null) {
                filesProcessedCounter.increment();
                if (schemaType == SchemaType.GREEN) {
                    if (greenTaxiFilesCounter != null) greenTaxiFilesCounter.increment();
                    if (greenTaxiRecordsCounter != null) greenTaxiRecordsCounter.increment(recordCount);
                } else {
                    if (yellowTaxiFilesCounter != null) yellowTaxiFilesCounter.increment();
                    if (yellowTaxiRecordsCounter != null) yellowTaxiRecordsCounter.increment(recordCount);
                }
                logger.info("Metrics updated for file: {}", fileName);
            }
            
            logger.info("Successfully processed {} records from file: {} into table {}", 
                recordCount, fileName, tableName);
            
            logger.info("About to move file {} to processed directory", fileName);
            // Move successfully processed file to processed directory
            moveToProcessedDirectory(file, processedPath);
            logger.info("File {} moved to processed directory successfully", fileName);
            
            processedFiles.add(file.toString());
            logger.info("File {} added to processedFiles set", fileName);
            
        } catch (IOException | SQLException e) {
            logger.error("Error processing file: {}", fileName, e);
            if (processingErrorsCounter != null) processingErrorsCounter.increment();
            try {
                moveToErrorDirectory(file, errorPath, "processing_error");
            } catch (IOException ioException) {
                logger.error("Failed to move file to error directory: {}", fileName, ioException);
            }
            if (filesProcessedCounter != null) filesProcessedCounter.increment();
            if (erroredFilesCounter != null) erroredFilesCounter.increment();
            processedFiles.add(file.toString());
        } catch (Exception e) {
            logger.error("Unexpected error processing file: {}", fileName, e);
            if (processingErrorsCounter != null) processingErrorsCounter.increment();
            try {
                moveToErrorDirectory(file, errorPath, "unexpected_error");
            } catch (IOException ioException) {
                logger.error("Failed to move file to error directory: {}", fileName, ioException);
            }
            if (filesProcessedCounter != null) filesProcessedCounter.increment();
            if (erroredFilesCounter != null) erroredFilesCounter.increment();
            processedFiles.add(file.toString());
        }
    }

    /**
     * Detects the schema type of a Parquet file by extracting the schema as JSON
     * and comparing it to the constants in TaxiParquetSchemas.
     * 
     * @param file the Parquet file to check
     * @return the matching schema type (GREEN, YELLOW, or UNKNOWN)
     */
    private SchemaType detectSchemaType(Path file) {
        try {
            // Extract schema as JSON from the parquet file
            String fileSchemaJson = extractSchemaAsJson(file);
            if (fileSchemaJson == null) {
                logger.warn("Could not extract schema from file: {}", file);
                return SchemaType.UNKNOWN;
            }
            
            logger.debug("Extracted schema from file: {}", file);
            
            // Compare with YELLOW schema constant
            if (schemasMatch(fileSchemaJson, TaxiParquetSchemas.YELLOW)) {
                logger.info("File {} matches YELLOW schema", file.getFileName());
                return SchemaType.YELLOW;
            }
            
            // Compare with GREEN schema constant
            if (schemasMatch(fileSchemaJson, TaxiParquetSchemas.GREEN)) {
                logger.info("File {} matches GREEN schema", file.getFileName());
                return SchemaType.GREEN;
            }
            
            logger.warn("File {} does not match YELLOW or GREEN schema", file.getFileName());
            return SchemaType.UNKNOWN;
        } catch (Exception e) {
            logger.error("Error detecting schema type for file: {}", file, e);
            return SchemaType.UNKNOWN;
        }
    }

    /**
     * Extracts the schema from a Parquet file as a JSON string.
     * 
     * @param file the Parquet file
     * @return the schema as a JSON string, or null if extraction fails
     */
    private String extractSchemaAsJson(Path file) {
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
                    logger.warn("Parquet file is empty: {}", file);
                    return null;
                }
                
                Schema schema = firstRecord.getSchema();
                // Avro schema toString() returns JSON format
                return schema.toString();
            }
        } catch (Exception e) {
            logger.error("Error extracting schema from file: {}", file, e);
            return null;
        }
    }

    /**
     * Compares two JSON schema strings by normalizing them and checking for equality.
     * This handles differences in whitespace and field ordering.
     * 
     * @param schema1 first schema JSON string
     * @param schema2 second schema JSON string
     * @return true if the schemas match, false otherwise
     */
    private boolean schemasMatch(String schema1, String schema2) {
        try {
            // Parse both schemas as JSON nodes
            JsonNode node1 = objectMapper.readTree(schema1.trim());
            JsonNode node2 = objectMapper.readTree(schema2.trim());
            
            // Compare the JSON structures (this handles field ordering differences)
            return node1.equals(node2);
        } catch (Exception e) {
            logger.debug("Error comparing schemas: {}", e.getMessage());
            return false;
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
     * Moves a successfully processed file to the processed directory.
     */
    private void moveToProcessedDirectory(Path file, Path processedDir) throws IOException {
        String fileName = file.getFileName().toString();
        Path processedFile = processedDir.resolve(fileName);
        Files.move(file, processedFile, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Moved successfully processed file {} to processed directory", fileName);
    }

    /**
     * Stops monitoring the directory.
     */
    public void stopMonitoring() {
        if (!running) {
            return;
        }
        
        logger.info("Stopping directory monitoring");
        running = false;
        
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                logger.error("Error closing WatchService", e);
            }
        }
        
        if (executorService != null) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        if (fileProcessingExecutorService != null) {
            fileProcessingExecutorService.shutdown();
            try {
                if (!fileProcessingExecutorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    fileProcessingExecutorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                fileProcessingExecutorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("Directory monitoring stopped");
    }

    @PreDestroy
    void cleanup() {
        stopMonitoring();
        logger.info("ParquetFileDirectoryMonitor cleaned up");
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

