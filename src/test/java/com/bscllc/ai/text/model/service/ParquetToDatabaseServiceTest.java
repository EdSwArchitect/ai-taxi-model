package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for the ParquetToDatabaseService.
 */
@DisplayName("ParquetToDatabaseService Tests")
class ParquetToDatabaseServiceTest {

    private ParquetToDatabaseService service;
    private String testDbUrl;
    private Connection testConnection;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Use PostgreSQL database (assumes PostgreSQL is running via docker-compose or locally)
        // Default to test database on localhost, can be overridden via system properties
        String dbHost = System.getProperty("test.db.host", "localhost");
        String dbPort = System.getProperty("test.db.port", "5432");
        String dbName = System.getProperty("test.db.name", "ai_taxi_model");
        String dbUsername = System.getProperty("test.db.username", "postgres");
        String dbPassword = System.getProperty("test.db.password", "postgres");
        String dbSchema = System.getProperty("test.db.schema", "public");
        
        testDbUrl = String.format("jdbc:postgresql://%s:%s/%s", dbHost, dbPort, dbName);
        
        // Set system properties for the service
        System.setProperty("db.url", testDbUrl);
        System.setProperty("db.username", dbUsername);
        System.setProperty("db.password", dbPassword);
        System.setProperty("db.schema", dbSchema);

        // Test database connection - skip tests if PostgreSQL is not available
        boolean dbAvailable = false;
        try {
            testConnection = DriverManager.getConnection(testDbUrl, dbUsername, dbPassword);
            // Verify connection works
            try (Statement stmt = testConnection.createStatement()) {
                stmt.executeQuery("SELECT 1");
            }
            dbAvailable = true;
        } catch (SQLException e) {
            System.out.println("WARNING: PostgreSQL not available at " + testDbUrl + 
                ". Skipping database tests. Start PostgreSQL with: docker-compose up -d postgres");
            System.out.println("To run tests, ensure PostgreSQL is running or set test.db.* system properties");
            testConnection = null;
        }
        
        // Skip tests if database is not available
        org.junit.jupiter.api.Assumptions.assumeTrue(dbAvailable, 
            "PostgreSQL database not available. Start with: docker-compose up -d postgres");

        // Initialize the service
        service = new ParquetToDatabaseService();
        service.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up test tables created during tests
        if (testConnection != null && !testConnection.isClosed()) {
            try (Statement stmt = testConnection.createStatement()) {
                // Get list of tables created during tests and drop them
                // Test tables typically start with "yellow_taxi_test", "green_taxi_test", etc.
                String schema = System.getProperty("test.db.schema", "public");
                ResultSet rs = stmt.executeQuery(
                    "SELECT tablename FROM pg_tables WHERE schemaname = '" + schema + "' " +
                    "AND (tablename LIKE '%_test' OR tablename LIKE '%test_%' " +
                    "OR tablename LIKE 'yellow_tripdata_%' OR tablename LIKE 'green_tripdata_%' " +
                    "OR tablename LIKE 'test_table%' OR tablename LIKE 'test-%' " +
                    "OR tablename LIKE 'greentaxi_%' OR tablename LIKE 'yellowtaxi_%')"
                );
                
                while (rs.next()) {
                    String tableName = rs.getString("tablename");
                    try {
                        stmt.execute("DROP TABLE IF EXISTS " + schema + ".\"" + tableName + "\" CASCADE");
                    } catch (SQLException e) {
                        // Ignore errors during cleanup
                        System.err.println("Warning: Could not drop table " + tableName + ": " + e.getMessage());
                    }
                }
            } catch (SQLException e) {
                // Ignore cleanup errors
                System.err.println("Warning: Error during test cleanup: " + e.getMessage());
            } finally {
                testConnection.close();
            }
        }

        // Clean up system properties
        System.clearProperty("db.url");
        System.clearProperty("db.username");
        System.clearProperty("db.password");
        System.clearProperty("db.schema");

        // Cleanup service
        if (service != null) {
            service.cleanup();
        }
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

    /**
     * Helper method to check if a table exists in the database.
     */
    private boolean tableExists(String tableName) throws SQLException {
        String schema = System.getProperty("test.db.schema", "public");
        try (Statement stmt = testConnection.createStatement()) {
            // PostgreSQL uses lowercase identifiers unless quoted, check both
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM pg_tables " +
                "WHERE schemaname = '" + schema + "' " +
                "AND tablename = '" + tableName.toLowerCase() + "'"
            );
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        }
    }

    /**
     * Helper method to get the row count of a table.
     */
    private long getRowCount(String tableName) throws SQLException {
        String schema = System.getProperty("test.db.schema", "public");
        try (Statement stmt = testConnection.createStatement()) {
            // PostgreSQL uses lowercase identifiers unless quoted
            // ParquetToDatabaseService creates tables with lowercase names
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM " + schema + ".\"" + tableName.toLowerCase() + "\""
            );
            if (rs.next()) {
                return rs.getLong(1);
            }
            return 0;
        }
    }

    @Test
    @DisplayName("Should process yellow taxi Parquet file and create table with records")
    void testProcessYellowTaxiParquetFile() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        String tableName = "yellow_taxi_test";

        // Process the Parquet file
        long recordCount = service.processParquetFile(testFile, tableName, true);

        // Verify table was created
        assertTrue(tableExists(tableName), "Table should be created");

        // Verify records were inserted
        long dbRowCount = getRowCount(tableName);
        assertEquals(recordCount, dbRowCount, "All records should be inserted");
        assertTrue(recordCount > 0, "Should have inserted records");

        // Verify table structure by checking a few columns
        String schema = System.getProperty("test.db.schema", "public");
        try (Statement stmt = testConnection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT * FROM " + schema + ".\"" + tableName.toLowerCase() + "\" LIMIT 1"
            );
            
            if (rs.next()) {
                // Check that key columns exist
                assertTrue(rs.getMetaData().getColumnCount() > 0, "Table should have columns");
                
                // Verify some expected columns exist
                int columnCount = rs.getMetaData().getColumnCount();
                boolean hasVendorId = false;
                boolean hasPickupDateTime = false;
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    if (columnName.equalsIgnoreCase("VendorID") || columnName.equalsIgnoreCase("vendorid")) {
                        hasVendorId = true;
                    }
                    if (columnName.equalsIgnoreCase("TPEP_PICKUP_DATETIME") || 
                        columnName.equalsIgnoreCase("tpep_pickup_datetime")) {
                        hasPickupDateTime = true;
                    }
                }
                
                assertTrue(hasVendorId, "Table should have VendorID column");
                assertTrue(hasPickupDateTime, "Table should have tpep_pickup_datetime column");
            }
        }
    }

    @Test
    @DisplayName("Should process green taxi Parquet file and create table with records")
    void testProcessGreenTaxiParquetFile() throws Exception {
        // Find the test green parquet file
        Path testFile = findTestParquetFile("green_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: green_tripdata_2025_01.parquet not found");
            return;
        }

        String tableName = "green_taxi_test";

        // Process the Parquet file
        long recordCount = service.processParquetFile(testFile, tableName, true);

        // Verify table was created
        assertTrue(tableExists(tableName), "Table should be created");

        // Verify records were inserted
        long dbRowCount = getRowCount(tableName);
        assertEquals(recordCount, dbRowCount, "All records should be inserted");
        assertTrue(recordCount > 0, "Should have inserted records");

        // Verify table structure
        String schema = System.getProperty("test.db.schema", "public");
        try (Statement stmt = testConnection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT * FROM " + schema + ".\"" + tableName.toLowerCase() + "\" LIMIT 1"
            );
            
            if (rs.next()) {
                int columnCount = rs.getMetaData().getColumnCount();
                boolean hasVendorId = false;
                boolean hasPickupDateTime = false;
                
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = rs.getMetaData().getColumnName(i);
                    if (columnName.equalsIgnoreCase("VendorID") || columnName.equalsIgnoreCase("vendorid")) {
                        hasVendorId = true;
                    }
                    if (columnName.equalsIgnoreCase("LPEP_PICKUP_DATETIME") || 
                        columnName.equalsIgnoreCase("lpep_pickup_datetime")) {
                        hasPickupDateTime = true;
                    }
                }
                
                assertTrue(hasVendorId, "Table should have VendorID column");
                assertTrue(hasPickupDateTime, "Table should have lpep_pickup_datetime column");
            }
        }
    }

    @Test
    @DisplayName("Should derive table name from file name when not provided")
    void testDeriveTableNameFromFileName() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Process without specifying table name
        long recordCount = service.processParquetFile(testFile, null, true);

        // Verify table was created with derived name
        String expectedTableName = "yellow_tripdata_2025_01";
        assertTrue(tableExists(expectedTableName), "Table should be created with derived name");

        // Verify records were inserted
        long dbRowCount = getRowCount(expectedTableName);
        assertEquals(recordCount, dbRowCount, "All records should be inserted");
    }

    @Test
    @DisplayName("Should drop and recreate table when dropIfExists is true")
    void testDropTableIfExists() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        String tableName = "test_drop_table";

        // Process the file first time
        long recordCount1 = service.processParquetFile(testFile, tableName, true);
        assertTrue(tableExists(tableName), "Table should be created");
        assertEquals(recordCount1, getRowCount(tableName), "Records should be inserted");

        // Process the file second time with dropIfExists = true
        long recordCount2 = service.processParquetFile(testFile, tableName, true);
        assertTrue(tableExists(tableName), "Table should still exist");
        assertEquals(recordCount2, getRowCount(tableName), "Table should have new records");
        assertEquals(recordCount1, recordCount2, "Record counts should match");
    }

    @Test
    @DisplayName("Should throw exception when file does not exist")
    void testProcessNonExistentFile() {
        Path nonExistentFile = tempDir.resolve("nonexistent.parquet");
        
        assertThrows(IOException.class, () -> {
            service.processParquetFile(nonExistentFile, "test_table", true);
        });
    }

    @Test
    @DisplayName("Should throw exception when path is not a file")
    void testProcessDirectory() {
        assertThrows(IOException.class, () -> {
            service.processParquetFile(tempDir, "test_table", true);
        });
    }

    @Test
    @DisplayName("Should handle empty Parquet file gracefully")
    void testProcessEmptyParquetFile() throws Exception {
        // Create an empty file (this will fail when trying to read schema)
        Path emptyFile = tempDir.resolve("empty.parquet");
        Files.createFile(emptyFile);
        
        // Should throw IOException when trying to read empty file
        assertThrows(IOException.class, () -> {
            service.processParquetFile(emptyFile, "empty_table", true);
        });
    }

    @Test
    @DisplayName("Should sanitize table names with special characters")
    void testSanitizeTableName() throws Exception {
        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Use a table name with special characters
        String tableNameWithSpecialChars = "test-table@2025#01";
        
        // Process the file - should sanitize the table name
        long recordCount = service.processParquetFile(testFile, tableNameWithSpecialChars, true);

        // The sanitized name should be "test_table_2025_01"
        String sanitizedTableName = "test_table_2025_01";
        assertTrue(tableExists(sanitizedTableName), "Table should be created with sanitized name");
        
        // Verify records were inserted
        long dbRowCount = getRowCount(sanitizedTableName);
        assertEquals(recordCount, dbRowCount, "All records should be inserted");
    }

    @Test
    @DisplayName("Should handle database connection errors gracefully")
    void testDatabaseConnectionError() throws Exception {
        // Set invalid database URL
        System.setProperty("db.url", "jdbc:postgresql://invalid:5432/nonexistent");
        
        // Reinitialize service with invalid connection
        ParquetToDatabaseService invalidService = new ParquetToDatabaseService();
        invalidService.init();

        // Find the test yellow parquet file
        Path testFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (testFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Should throw SQLException when trying to connect
        assertThrows(SQLException.class, () -> {
            invalidService.processParquetFile(testFile, "test_table", true);
        });

        // Cleanup invalid service
        if (invalidService != null) {
            invalidService.cleanup();
        }
        
        // Restore valid URL (will be cleaned up in tearDown)
        System.setProperty("db.url", testDbUrl);
    }
}

