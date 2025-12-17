package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Service that reads a Parquet file, extracts its schema, creates a database table
 * based on the schema, and inserts all records into the table.
 */
@ApplicationScoped
public class ParquetToDatabaseService {

    private static final Logger logger = LogManager.getLogger(ParquetToDatabaseService.class);

    @Inject
    MeterRegistry meterRegistry;
    
    @ConfigProperty(name = "parquet.database.batch.size", defaultValue = "1000")
    int batchSize;

    private String dbUrl;
    private String dbUsername;
    private String dbPassword;
    private String dbSchema;
    private boolean dbSslEnabled;
    private String dbSslMode;
    private String dbSslRootCert;
    private String dbSslCert;
    private String dbSslKey;

    // Micrometer metrics
    private Counter filesProcessedCounter;
    private Counter recordsInsertedCounter;
    private Counter tablesCreatedCounter;
    private Counter batchOperationsCounter;
    private Counter batchInsertSuccessCounter;
    private Counter batchInsertFailureCounter;
    private Counter databaseErrorsCounter;
    private Timer processingTimeTimer;

    @PostConstruct
    void init() {
        dbUrl = System.getProperty("db.url", "jdbc:postgresql://localhost:5432/ai_taxi_model");
        dbUsername = System.getProperty("db.username", "postgres");
        dbPassword = System.getProperty("db.password", "postgres");
        dbSchema = System.getProperty("db.schema", "public");
        
        // SSL/TLS configuration for PostgreSQL connection
        dbSslEnabled = Boolean.parseBoolean(System.getProperty("db.ssl.enabled", "false"));
        dbSslMode = System.getProperty("db.ssl.mode", "require"); // require, verify-ca, verify-full, prefer, allow, disable
        dbSslRootCert = System.getProperty("db.ssl.rootcert", ""); // Path to root CA certificate
        dbSslCert = System.getProperty("db.ssl.cert", ""); // Path to client certificate
        dbSslKey = System.getProperty("db.ssl.key", ""); // Path to client key
        
        initMetrics();
        
        logger.info("ParquetToDatabaseService initialized with database URL: {}", dbUrl);
        if (dbSslEnabled) {
            logger.info("Database SSL/TLS enabled with mode: {}", dbSslMode);
        }
    }

    /**
     * Initializes Micrometer metrics for database processing.
     */
    void initMetrics() {
        filesProcessedCounter = Counter.builder("parquet.database.files.processed")
                .description("Total number of Parquet files processed to database")
                .register(meterRegistry);
        
        recordsInsertedCounter = Counter.builder("parquet.database.records.inserted")
                .description("Total number of records inserted into database")
                .register(meterRegistry);
        
        tablesCreatedCounter = Counter.builder("parquet.database.tables.created")
                .description("Total number of database tables created")
                .register(meterRegistry);
        
        batchOperationsCounter = Counter.builder("parquet.database.batch.operations")
                .description("Total number of batch insert operations performed")
                .register(meterRegistry);
        
        batchInsertSuccessCounter = Counter.builder("parquet.database.batch.insert.success")
                .description("Total number of successful batch inserts")
                .register(meterRegistry);
        
        batchInsertFailureCounter = Counter.builder("parquet.database.batch.insert.failure")
                .description("Total number of failed batch inserts")
                .register(meterRegistry);
        
        databaseErrorsCounter = Counter.builder("parquet.database.errors")
                .description("Total number of database errors encountered")
                .register(meterRegistry);
        
        processingTimeTimer = Timer.builder("parquet.database.processing.time")
                .description("Time taken to process Parquet files to database")
                .register(meterRegistry);
    }

    /**
     * Processes a Parquet file by reading its schema, creating a table, and inserting records.
     *
     * @param filePath the path to the Parquet file
     * @param tableName the name of the table to create (if null, derived from file name)
     * @param dropIfExists if true, drops the table if it already exists
     * @return the number of records inserted
     * @throws IOException if there's an error reading the file
     * @throws SQLException if there's an error with database operations
     */
    public long processParquetFile(Path filePath, String tableName, boolean dropIfExists) 
            throws IOException, SQLException {
        
        if (!Files.exists(filePath)) {
            throw new IOException("File does not exist: " + filePath);
        }
        
        if (!Files.isRegularFile(filePath)) {
            throw new IOException("Path is not a regular file: " + filePath);
        }

        logger.info("Processing Parquet file: {}", filePath);

        // Measure processing time using Timer.Sample
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Determine table name if not provided
            if (tableName == null || tableName.trim().isEmpty()) {
                tableName = deriveTableName(filePath);
            }
            tableName = sanitizeTableName(tableName);

            // Read schema from Parquet file
            Schema avroSchema = readSchema(filePath);
            logger.info("Read Avro schema with {} fields", avroSchema.getFields().size());

            // Create database connection
            try (Connection connection = getConnection()) {
                // Ensure auto-commit is off for batch operations, but commit explicitly
                boolean originalAutoCommit = connection.getAutoCommit();
                try {
                    connection.setAutoCommit(false);
                    
                    // Create table based on schema
                    createTable(connection, tableName, avroSchema, dropIfExists);
                    
                    // Insert records (which will commit each batch)
                    long recordCount = insertRecords(connection, filePath, tableName, avroSchema);
                    
                    // Final commit to ensure all data is persisted
                    connection.commit();
                    
                    // Update metrics
                    filesProcessedCounter.increment();
                    recordsInsertedCounter.increment(recordCount);
                    tablesCreatedCounter.increment();
                    
                    logger.info("Successfully processed {} records from {} into table {}", 
                        recordCount, filePath.getFileName(), tableName);
                    
                    // Record successful processing time
                    sample.stop(processingTimeTimer);
                    
                    return recordCount;
                } finally {
                    // Restore original auto-commit setting
                    connection.setAutoCommit(originalAutoCommit);
                }
            }
        } catch (SQLException e) {
            // Increment error counter
            databaseErrorsCounter.increment();
            filesProcessedCounter.increment(); // Count file as processed even if it failed
            
            // Record failed processing time
            sample.stop(processingTimeTimer);
            
            throw e;
        } catch (IOException e) {
            // Record failed processing time
            sample.stop(processingTimeTimer);
            throw e;
        }
    }

    /**
     * Reads the Avro schema from a Parquet file.
     */
    private Schema readSchema(Path filePath) throws IOException {
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);

        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(filePath.toUri());
        InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);

        try (ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            GenericRecord firstRecord = reader.read();
            if (firstRecord == null) {
                throw new IOException("Parquet file is empty: " + filePath);
            }
            return firstRecord.getSchema();
        }
    }

    /**
     * Creates a database table based on the Avro schema.
     */
    private void createTable(Connection connection, String tableName, Schema avroSchema, boolean dropIfExists) 
            throws SQLException {
        
        try (Statement stmt = connection.createStatement()) {
            // Drop table if exists and dropIfExists is true
            if (dropIfExists) {
                String dropSql = String.format("DROP TABLE IF EXISTS %s.%s CASCADE", 
                    quoteIdentifier(dbSchema), quoteIdentifier(tableName));
                logger.info("Dropping table if exists: {}", dropSql);
                stmt.execute(dropSql);
            }

            // Build CREATE TABLE statement
            StringBuilder createSql = new StringBuilder();
            createSql.append("CREATE TABLE ").append(quoteIdentifier(dbSchema))
                     .append(".").append(quoteIdentifier(tableName)).append(" (");

            List<String> columns = new ArrayList<>();
            for (Schema.Field field : avroSchema.getFields()) {
                String columnName = sanitizeColumnName(field.name());
                String sqlType = avroTypeToSqlType(field.schema());
                columns.add(quoteIdentifier(columnName) + " " + sqlType);
            }

            createSql.append(String.join(", ", columns));
            createSql.append(")");

            logger.info("Creating table: {}", createSql);
            stmt.execute(createSql.toString());
            logger.info("Table {} created successfully", tableName);
        }
    }

    /**
     * Inserts records from the Parquet file into the database table.
     */
    private long insertRecords(Connection connection, Path filePath, String tableName, Schema avroSchema) 
            throws IOException, SQLException {
        
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);

        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(filePath.toUri());
        InputFile inputFile = HadoopInputFile.fromPath(hadoopPath, conf);

        List<Schema.Field> fields = avroSchema.getFields();
        List<String> columnNames = fields.stream()
            .map(f -> sanitizeColumnName(f.name()))
            .toList();

        // Build INSERT statement
        String insertSql = buildInsertStatement(tableName, columnNames);
        logger.info("Insert SQL: {}", insertSql);

        long recordCount = 0;
        List<GenericRecord> batch = new ArrayList<>();

        try (ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            
            GenericRecord record;
            while ((record = reader.read()) != null) {
                batch.add(record);
                
                if (batch.size() >= batchSize) {
                    insertBatch(connection, insertSql, batch, fields);
                    recordCount += batch.size();
                    batch.clear();
                }
            }

            // Insert remaining records
            if (!batch.isEmpty()) {
                insertBatch(connection, insertSql, batch, fields);
                recordCount += batch.size();
            }
        }

        return recordCount;
    }

    /**
     * Inserts a batch of records into the database.
     */
    private void insertBatch(Connection connection, String insertSql, List<GenericRecord> batch, 
                            List<Schema.Field> fields) throws SQLException {
        
        batchOperationsCounter.increment();
        
        try (PreparedStatement pstmt = connection.prepareStatement(insertSql)) {
            for (GenericRecord record : batch) {
                setParameters(pstmt, record, fields);
                pstmt.addBatch();
            }
            
            int[] results = pstmt.executeBatch();
            int successCount = 0;
            int failureCount = 0;
            
            for (int result : results) {
                if (result >= 0) {
                    successCount++;
                } else {
                    failureCount++;
                }
            }
            
            // Explicitly commit the batch to ensure data is visible immediately
            connection.commit();
            
            // Update batch metrics
            if (failureCount == 0) {
                batchInsertSuccessCounter.increment();
            } else {
                batchInsertFailureCounter.increment();
                logger.warn("Batch insert had {} failures out of {} records", failureCount, batch.size());
            }
            
            logger.debug("Inserted and committed batch of {} records ({} successful, {} failed)", 
                batch.size(), successCount, failureCount);
        } catch (SQLException e) {
            batchInsertFailureCounter.increment();
            databaseErrorsCounter.increment();
            // Rollback on error
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                logger.error("Failed to rollback transaction", rollbackEx);
            }
            throw e;
        }
    }

    /**
     * Sets parameters for a PreparedStatement from a GenericRecord.
     */
    private void setParameters(PreparedStatement pstmt, GenericRecord record, List<Schema.Field> fields) 
            throws SQLException {
        
        for (int i = 0; i < fields.size(); i++) {
            Schema.Field field = fields.get(i);
            Object value = record.get(field.name());
            int paramIndex = i + 1;
            
            if (value == null) {
                pstmt.setNull(paramIndex, Types.NULL);
            } else {
                setParameter(pstmt, paramIndex, value, field.schema());
            }
        }
    }

    /**
     * Sets a single parameter based on its Avro type.
     */
    private void setParameter(PreparedStatement pstmt, int index, Object value, Schema schema) 
            throws SQLException {
        
        Schema.Type type = getNonNullType(schema);
        
        switch (type) {
            case INT:
                pstmt.setInt(index, ((Number) value).intValue());
                break;
            case LONG:
                // Check if it's a timestamp logical type
                if (schema.getLogicalType() != null && 
                    schema.getLogicalType().getName().equals("timestamp-micros")) {
                    // Handle timestamp microseconds
                    long microseconds = ((Number) value).longValue();
                    long seconds = microseconds / 1_000_000;
                    long nanos = (microseconds % 1_000_000) * 1_000;
                    Timestamp timestamp = Timestamp.from(
                        java.time.Instant.ofEpochSecond(seconds, nanos));
                    pstmt.setTimestamp(index, timestamp);
                } else {
                    pstmt.setLong(index, ((Number) value).longValue());
                }
                break;
            case FLOAT:
                pstmt.setFloat(index, ((Number) value).floatValue());
                break;
            case DOUBLE:
                pstmt.setDouble(index, ((Number) value).doubleValue());
                break;
            case BOOLEAN:
                pstmt.setBoolean(index, (Boolean) value);
                break;
            case STRING:
                pstmt.setString(index, value.toString());
                break;
            case BYTES:
                pstmt.setBytes(index, (byte[]) value);
                break;
            default:
                // For complex types (timestamp, date, etc.), convert to string or timestamp
                if (value instanceof Long && schema.getLogicalType() != null) {
                    // Handle timestamp microseconds
                    long microseconds = (Long) value;
                    long seconds = microseconds / 1_000_000;
                    long nanos = (microseconds % 1_000_000) * 1_000;
                    Timestamp timestamp = Timestamp.from(
                        java.time.Instant.ofEpochSecond(seconds, nanos));
                    pstmt.setTimestamp(index, timestamp);
                } else {
                    // Default: convert to string
                    pstmt.setString(index, value.toString());
                }
                break;
        }
    }

    /**
     * Gets the non-null type from a schema (handles UNION types with null).
     */
    private Schema.Type getNonNullType(Schema schema) {
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema subSchema : schema.getTypes()) {
                if (subSchema.getType() != Schema.Type.NULL) {
                    return subSchema.getType();
                }
            }
        }
        return schema.getType();
    }

    /**
     * Converts an Avro type to a SQL type string.
     */
    private String avroTypeToSqlType(Schema schema) {
        Schema.Type type = getNonNullType(schema);
        boolean nullable = schema.getType() == Schema.Type.UNION && 
                         schema.getTypes().stream().anyMatch(s -> s.getType() == Schema.Type.NULL);

        // Get the actual non-null schema for logical type checking
        Schema actualSchema = schema;
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema subSchema : schema.getTypes()) {
                if (subSchema.getType() != Schema.Type.NULL) {
                    actualSchema = subSchema;
                    break;
                }
            }
        }

        String sqlType;
        switch (type) {
            case INT:
                sqlType = "INTEGER";
                break;
            case LONG:
                // Check for timestamp logical type
                if (actualSchema.getLogicalType() != null && 
                    actualSchema.getLogicalType().getName().equals("timestamp-micros")) {
                    sqlType = "TIMESTAMP";
                } else {
                    sqlType = "BIGINT";
                }
                break;
            case FLOAT:
                sqlType = "REAL";
                break;
            case DOUBLE:
                sqlType = "DOUBLE PRECISION";
                break;
            case BOOLEAN:
                sqlType = "BOOLEAN";
                break;
            case STRING:
                sqlType = "TEXT";
                break;
            case BYTES:
                sqlType = "BYTEA";
                break;
            default:
                // For unknown types, use TEXT
                sqlType = "TEXT";
                break;
        }

        return nullable ? sqlType : sqlType + " NOT NULL";
    }

    /**
     * Builds an INSERT statement.
     */
    private String buildInsertStatement(String tableName, List<String> columnNames) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(quoteIdentifier(dbSchema))
           .append(".").append(quoteIdentifier(tableName)).append(" (");
        
        sql.append(columnNames.stream()
            .map(this::quoteIdentifier)
            .reduce((a, b) -> a + ", " + b)
            .orElse(""));
        
        sql.append(") VALUES (");
        sql.append("?, ".repeat(columnNames.size()));
        sql.setLength(sql.length() - 2); // Remove trailing ", "
        sql.append(")");
        
        return sql.toString();
    }

    /**
     * Derives a table name from the file path.
     */
    private String deriveTableName(Path filePath) {
        String fileName = filePath.getFileName().toString();
        // Remove extension
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            fileName = fileName.substring(0, lastDot);
        }
        return fileName;
    }

    /**
     * Sanitizes a table name to be SQL-safe.
     */
    private String sanitizeTableName(String name) {
        // Replace invalid characters with underscore
        return name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
    }

    /**
     * Sanitizes a column name to be SQL-safe.
     */
    private String sanitizeColumnName(String name) {
        // Replace invalid characters with underscore, but preserve case
        return name.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    /**
     * Quotes an identifier for SQL.
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    /**
     * Gets a database connection with SSL/TLS support if enabled.
     */
    private Connection getConnection() throws SQLException {
        if (dbSslEnabled) {
            // Build connection URL with SSL parameters
            String urlWithSsl = buildConnectionUrlWithSsl();
            Properties props = new Properties();
            props.setProperty("user", dbUsername);
            props.setProperty("password", dbPassword);
            
            // Add SSL connection properties
            props.setProperty("ssl", "true");
            props.setProperty("sslmode", dbSslMode);
            
            // Add certificate paths if provided
            if (dbSslRootCert != null && !dbSslRootCert.trim().isEmpty()) {
                props.setProperty("sslrootcert", dbSslRootCert);
            }
            if (dbSslCert != null && !dbSslCert.trim().isEmpty()) {
                props.setProperty("sslcert", dbSslCert);
            }
            if (dbSslKey != null && !dbSslKey.trim().isEmpty()) {
                props.setProperty("sslkey", dbSslKey);
            }
            
            // For self-signed certificates in development, we might need to disable hostname verification
            // This is similar to OpenSearchService's approach with NoopHostnameVerifier
            if ("require".equalsIgnoreCase(dbSslMode) || "prefer".equalsIgnoreCase(dbSslMode)) {
                // sslmode=require doesn't verify certificates, similar to trust-all in OpenSearchService
                logger.debug("Using SSL mode '{}' which accepts self-signed certificates", dbSslMode);
            }
            
            logger.debug("Connecting to database with SSL enabled (mode: {})", dbSslMode);
            return DriverManager.getConnection(urlWithSsl, props);
        } else {
            // Standard connection without SSL
            return DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
        }
    }
    
    /**
     * Builds the JDBC connection URL with SSL parameters.
     * If the URL already contains parameters, appends SSL parameters.
     */
    private String buildConnectionUrlWithSsl() {
        String baseUrl = dbUrl;
        
        // Check if URL already has parameters
        if (baseUrl.contains("?")) {
            // URL already has parameters, append SSL parameters
            if (!baseUrl.contains("ssl=")) {
                baseUrl += "&ssl=true";
            }
            if (!baseUrl.contains("sslmode=")) {
                baseUrl += "&sslmode=" + dbSslMode;
            }
        } else {
            // No existing parameters, add SSL parameters
            baseUrl += "?ssl=true&sslmode=" + dbSslMode;
        }
        
        // Add certificate paths if provided
        if (dbSslRootCert != null && !dbSslRootCert.trim().isEmpty()) {
            baseUrl += "&sslrootcert=" + dbSslRootCert;
        }
        if (dbSslCert != null && !dbSslCert.trim().isEmpty()) {
            baseUrl += "&sslcert=" + dbSslCert;
        }
        if (dbSslKey != null && !dbSslKey.trim().isEmpty()) {
            baseUrl += "&sslkey=" + dbSslKey;
        }
        
        return baseUrl;
    }

    @PreDestroy
    void cleanup() {
        logger.info("ParquetToDatabaseService shutting down");
    }
}


