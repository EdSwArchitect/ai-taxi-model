package com.bscllc.ai.text.model.datamodel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bscllc.ai.text.model.input.YellowReader;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for YellowTaxi serialization using actual Parquet files.
 */
@DisplayName("YellowTaxi Serialization Tests")
class YellowTaxiSerializationTest {

    private YellowReader reader;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        reader = new YellowReader();
        
        // Configure ObjectMapper for JSON transformation
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
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

    @Test
    @DisplayName("Should serialize YellowTaxi records from yellow_tripdata_2025_01.parquet to JSON")
    void testSerializeYellowTaxiFromParquetFile() throws Exception {
        // Find the test Parquet file
        Path parquetFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (parquetFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Read records from Parquet file
        List<YellowTaxi> taxis = reader.readAll(parquetFile);
        
        assertNotNull(taxis, "List of taxis should not be null");
        assertFalse(taxis.isEmpty(), "List of taxis should not be empty");

        // Test serialization of first record
        YellowTaxi firstTaxi = taxis.get(0);
        String json = objectMapper.writeValueAsString(firstTaxi);
        
        assertNotNull(json, "JSON should not be null");
        assertFalse(json.isEmpty(), "JSON should not be empty");
        
        // Verify JSON contains expected fields
        assertTrue(json.contains("VendorID"), "JSON should contain VendorID");
        assertTrue(json.contains("tpep_pickup_datetime"), "JSON should contain tpep_pickup_datetime");
        assertTrue(json.contains("tpep_dropoff_datetime"), "JSON should contain tpep_dropoff_datetime");
        assertTrue(json.contains("passenger_count"), "JSON should contain passenger_count");
        assertTrue(json.contains("trip_distance"), "JSON should contain trip_distance");
        assertTrue(json.contains("airport_fee"), "JSON should contain airport_fee");
        assertTrue(json.contains("cbd_congestion_fee"), "JSON should contain cbd_congestion_fee");
        
        // Verify JSON contains the vendor ID value
        assertTrue(json.contains(String.valueOf(firstTaxi.vendorID())), 
            "JSON should contain vendor ID value");
    }

    @Test
    @DisplayName("Should round-trip serialize and deserialize YellowTaxi from yellow_tripdata_2025_01.parquet")
    void testRoundTripSerializationFromParquetFile() throws Exception {
        // Find the test Parquet file
        Path parquetFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (parquetFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Read records from Parquet file
        List<YellowTaxi> taxis = reader.readAll(parquetFile);
        
        assertNotNull(taxis, "List of taxis should not be null");
        assertFalse(taxis.isEmpty(), "List of taxis should not be empty");

        // Test round-trip serialization for first 10 records
        int recordsToTest = Math.min(10, taxis.size());
        int successfulRoundTrips = 0;
        
        for (int i = 0; i < recordsToTest; i++) {
            YellowTaxi original = taxis.get(i);
            
            // Skip invalid records
            if (!original.isValid()) {
                continue;
            }
            
            try {
                // Serialize to JSON
                String json = objectMapper.writeValueAsString(original);
                assertNotNull(json, "JSON should not be null for record " + i);
                
                // Deserialize back to object
                YellowTaxi deserialized = objectMapper.readValue(json, YellowTaxi.class);
                assertNotNull(deserialized, "Deserialized object should not be null for record " + i);
                
                // Verify all fields match
                assertEquals(original.vendorID(), deserialized.vendorID(), 
                    "VendorID should match for record " + i);
                assertEquals(original.tpepPickupDateTime(), deserialized.tpepPickupDateTime(), 
                    "Pickup datetime should match for record " + i);
                assertEquals(original.tpepDropoffDateTime(), deserialized.tpepDropoffDateTime(), 
                    "Dropoff datetime should match for record " + i);
                assertEquals(original.storeAndFwdFlag(), deserialized.storeAndFwdFlag(), 
                    "Store and forward flag should match for record " + i);
                assertEquals(original.passengerCount(), deserialized.passengerCount(), 
                    "Passenger count should match for record " + i);
                assertEquals(original.tripDistance(), deserialized.tripDistance(), 
                    "Trip distance should match for record " + i);
                assertEquals(original.ratecodeID(), deserialized.ratecodeID(), 
                    "Ratecode ID should match for record " + i);
                assertEquals(original.puLocationID(), deserialized.puLocationID(), 
                    "PU location ID should match for record " + i);
                assertEquals(original.doLocationID(), deserialized.doLocationID(), 
                    "DO location ID should match for record " + i);
                assertEquals(original.paymentType(), deserialized.paymentType(), 
                    "Payment type should match for record " + i);
                assertEquals(original.fareAmount(), deserialized.fareAmount(), 
                    "Fare amount should match for record " + i);
                assertEquals(original.extra(), deserialized.extra(), 
                    "Extra should match for record " + i);
                assertEquals(original.mtaTax(), deserialized.mtaTax(), 
                    "MTA tax should match for record " + i);
                assertEquals(original.tipAmount(), deserialized.tipAmount(), 
                    "Tip amount should match for record " + i);
                assertEquals(original.tollsAmount(), deserialized.tollsAmount(), 
                    "Tolls amount should match for record " + i);
                assertEquals(original.improvementSurcharge(), deserialized.improvementSurcharge(), 
                    "Improvement surcharge should match for record " + i);
                assertEquals(original.totalAmount(), deserialized.totalAmount(), 
                    "Total amount should match for record " + i);
                assertEquals(original.congestionSurcharge(), deserialized.congestionSurcharge(), 
                    "Congestion surcharge should match for record " + i);
                assertEquals(original.airportFee(), deserialized.airportFee(), 
                    "Airport fee should match for record " + i);
                assertEquals(original.cbdCongestionFee(), deserialized.cbdCongestionFee(), 
                    "CBD congestion fee should match for record " + i);
                
                successfulRoundTrips++;
            } catch (Exception e) {
                System.err.println("Failed round-trip for record " + i + ": " + e.getMessage());
                // Continue with other records
            }
        }
        
        assertTrue(successfulRoundTrips > 0, 
            String.format("At least one record should successfully round-trip. Successfully tested: %d/%d", 
                successfulRoundTrips, recordsToTest));
    }

    @Test
    @DisplayName("Should serialize all YellowTaxi records from yellow_tripdata_2025_01.parquet without errors")
    void testSerializeAllRecordsFromParquetFile() throws Exception {
        // Find the test Parquet file
        Path parquetFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (parquetFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Read all records from Parquet file
        List<YellowTaxi> taxis = reader.readAll(parquetFile);
        
        assertNotNull(taxis, "List of taxis should not be null");
        assertFalse(taxis.isEmpty(), "List of taxis should not be empty");

        int serializationErrors = 0;
        int serializedCount = 0;
        
        // Try to serialize all records
        for (YellowTaxi taxi : taxis) {
            try {
                String json = objectMapper.writeValueAsString(taxi);
                assertNotNull(json, "JSON should not be null");
                assertFalse(json.isEmpty(), "JSON should not be empty");
                serializedCount++;
            } catch (JsonProcessingException e) {
                serializationErrors++;
                System.err.println("Serialization error for taxi: " + e.getMessage());
            }
        }
        
        // Verify most records can be serialized
        double successRate = (double) serializedCount / taxis.size();
        assertTrue(successRate > 0.95, 
            String.format("At least 95%% of records should serialize successfully. Success rate: %.2f%% (%d/%d, errors: %d)", 
                successRate * 100, serializedCount, taxis.size(), serializationErrors));
        
        System.out.println(String.format(
            "Successfully serialized %d out of %d YellowTaxi records (%.2f%%, errors: %d)", 
            serializedCount, taxis.size(), successRate * 100, serializationErrors));
    }

    @Test
    @DisplayName("Should serialize YellowTaxi with all fields from yellow_tripdata_2025_01.parquet")
    void testSerializeWithAllFieldsFromParquetFile() throws Exception {
        // Find the test Parquet file
        Path parquetFile = findTestParquetFile("yellow_tripdata_2025_01.parquet");
        if (parquetFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Read records from Parquet file
        List<YellowTaxi> taxis = reader.readAll(parquetFile);
        
        assertNotNull(taxis, "List of taxis should not be null");
        assertFalse(taxis.isEmpty(), "List of taxis should not be empty");

        // Find a valid record with all fields populated
        YellowTaxi testTaxi = null;
        for (YellowTaxi taxi : taxis) {
            if (taxi.isValid() && 
                taxi.vendorID() != null &&
                taxi.tpepPickupDateTime() != null &&
                taxi.tpepDropoffDateTime() != null &&
                taxi.passengerCount() != null &&
                taxi.tripDistance() != null &&
                taxi.ratecodeID() != null &&
                taxi.storeAndFwdFlag() != null &&
                taxi.puLocationID() != null &&
                taxi.doLocationID() != null &&
                taxi.paymentType() != null &&
                taxi.fareAmount() != null) {
                testTaxi = taxi;
                break;
            }
        }
        
        assertNotNull(testTaxi, "Should find at least one valid taxi record with all fields");
        
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(testTaxi);
        assertNotNull(json, "JSON should not be null");
        
        // Verify all 20 fields are present in JSON
        String[] requiredFields = {
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
            "congestion_surcharge",
            "airport_fee",
            "cbd_congestion_fee"
        };
        
        for (String field : requiredFields) {
            assertTrue(json.contains(field), 
                String.format("JSON should contain field: %s", field));
        }
        
        System.out.println("Successfully serialized YellowTaxi with all 20 fields");
    }
}

