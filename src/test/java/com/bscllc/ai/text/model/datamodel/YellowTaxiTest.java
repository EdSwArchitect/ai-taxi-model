package com.bscllc.ai.text.model.datamodel;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Unit tests for the YellowTaxi record.
 */
@DisplayName("YellowTaxi Tests")
class YellowTaxiTest {

    private YellowTaxi validTaxi;
    private LocalDateTime pickupTime;
    private LocalDateTime dropoffTime;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        pickupTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        dropoffTime = LocalDateTime.of(2024, 1, 15, 11, 15, 0);
        
        validTaxi = new YellowTaxi(
                1, // vendorID
                pickupTime, // tpepPickupDateTime
                dropoffTime, // tpepDropoffDateTime
                2, // passengerCount
                5.5, // tripDistance
                1, // ratecodeID
                "N", // storeAndFwdFlag
                100, // puLocationID
                200, // doLocationID
                1, // paymentType
                15.50, // fareAmount
                1.00, // extra
                0.50, // mtaTax
                3.00, // tipAmount
                0.00, // tollsAmount
                0.30, // improvementSurcharge
                20.30, // totalAmount
                2.50, // congestionSurcharge
                0.00, // airportFee
                0.00 // cbdCongestionFee
        );

        // Configure ObjectMapper for JSON transformation
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    @DisplayName("Should create a valid YellowTaxi record")
    void testCreateValidYellowTaxi() {
        assertNotNull(validTaxi);
        assertEquals(1, validTaxi.vendorID());
        assertEquals(pickupTime, validTaxi.tpepPickupDateTime());
        assertEquals(dropoffTime, validTaxi.tpepDropoffDateTime());
        assertEquals(2, validTaxi.passengerCount());
        assertEquals(5.5, validTaxi.tripDistance());
    }

    @Test
    @DisplayName("Should validate a correct YellowTaxi record")
    void testIsValidWithValidRecord() {
        assertTrue(validTaxi.isValid());
    }

    @Test
    @DisplayName("Should fail validation with invalid vendorID")
    void testIsValidWithInvalidVendorID() {
        YellowTaxi invalidVendor = new YellowTaxi(
                99, // invalid vendorID
                pickupTime,
                dropoffTime,
                2,
                5.5,
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidVendor.isValid());
    }

    @Test
    @DisplayName("Should fail validation with null pickup datetime")
    void testIsValidWithNullPickupDateTime() {
        YellowTaxi invalidDateTime = new YellowTaxi(
                1,
                null, // null pickup time
                dropoffTime,
                2,
                5.5,
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidDateTime.isValid());
    }

    @Test
    @DisplayName("Should fail validation when dropoff is before pickup")
    void testIsValidWithDropoffBeforePickup() {
        YellowTaxi invalidOrder = new YellowTaxi(
                1,
                dropoffTime, // pickup after dropoff
                pickupTime, // dropoff before pickup
                2,
                5.5,
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidOrder.isValid());
    }

    @Test
    @DisplayName("Should fail validation with invalid storeAndFwdFlag")
    void testIsValidWithInvalidStoreAndFwdFlag() {
        YellowTaxi invalidFlag = new YellowTaxi(
                1,
                pickupTime,
                dropoffTime,
                2,
                5.5,
                1,
                "X", // invalid flag
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidFlag.isValid());
    }

    @Test
    @DisplayName("Should fail validation with negative passenger count")
    void testIsValidWithNegativePassengerCount() {
        YellowTaxi invalidPassengers = new YellowTaxi(
                1,
                pickupTime,
                dropoffTime,
                -1, // negative passenger count
                5.5,
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidPassengers.isValid());
    }

    @Test
    @DisplayName("Should fail validation with negative trip distance")
    void testIsValidWithNegativeTripDistance() {
        YellowTaxi invalidDistance = new YellowTaxi(
                1,
                pickupTime,
                dropoffTime,
                2,
                -1.0, // negative distance
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertFalse(invalidDistance.isValid());
    }

    @Test
    @DisplayName("Should calculate trip duration correctly")
    void testGetTripDurationMinutes() {
        Long duration = validTaxi.getTripDurationMinutes();
        assertNotNull(duration);
        assertEquals(45L, duration); // 45 minutes between 10:30 and 11:15
    }

    @Test
    @DisplayName("Should return null for trip duration with null dates")
    void testGetTripDurationMinutesWithNullDates() {
        YellowTaxi nullDates = new YellowTaxi(
                1,
                null,
                null,
                2,
                5.5,
                1,
                "N",
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertNull(nullDates.getTripDurationMinutes());
    }

    @Test
    @DisplayName("Should handle all valid vendor IDs")
    void testAllValidVendorIDs() {
        int[] validVendorIDs = {1, 2, 6, 7};
        
        for (int vendorID : validVendorIDs) {
            YellowTaxi taxi = new YellowTaxi(
                    vendorID,
                    pickupTime,
                    dropoffTime,
                    2,
                    5.5,
                    1,
                    "N",
                    100,
                    200,
                    1,
                    15.50,
                    1.00,
                    0.50,
                    3.00,
                    0.00,
                    0.30,
                    20.30,
                    2.50,
                    0.00,
                    0.00
            );
            assertTrue(taxi.isValid(), "VendorID " + vendorID + " should be valid");
        }
    }

    @Test
    @DisplayName("Should handle store and forward flag Y")
    void testStoreAndForwardFlagY() {
        YellowTaxi storeForward = new YellowTaxi(
                1,
                pickupTime,
                dropoffTime,
                2,
                5.5,
                1,
                "Y", // store and forward
                100,
                200,
                1,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                2.50,
                0.00,
                0.00
        );
        assertTrue(storeForward.isValid());
        assertEquals("Y", storeForward.storeAndFwdFlag());
    }

    @Test
    @DisplayName("Should handle different rate codes")
    void testDifferentRateCodes() {
        int[] rateCodes = {1, 2, 3, 4, 5, 6, 99};
        
        for (int rateCode : rateCodes) {
            YellowTaxi taxi = new YellowTaxi(
                    1,
                    pickupTime,
                    dropoffTime,
                    2,
                    5.5,
                    rateCode,
                    "N",
                    100,
                    200,
                    1,
                    15.50,
                    1.00,
                    0.50,
                    3.00,
                    0.00,
                    0.30,
                    20.30,
                    2.50,
                    0.00,
                    0.00
            );
            assertEquals(rateCode, taxi.ratecodeID());
        }
    }

    @Test
    @DisplayName("Should handle different payment types")
    void testDifferentPaymentTypes() {
        int[] paymentTypes = {0, 1, 2, 3, 4, 5, 6};
        
        for (int paymentType : paymentTypes) {
            YellowTaxi taxi = new YellowTaxi(
                    1,
                    pickupTime,
                    dropoffTime,
                    2,
                    5.5,
                    1,
                    "N",
                    100,
                    200,
                    paymentType,
                    15.50,
                    1.00,
                    0.50,
                    3.00,
                    0.00,
                    0.30,
                    20.30,
                    2.50,
                    0.00,
                    0.00
            );
            assertEquals(paymentType, taxi.paymentType());
        }
    }

    @Test
    @DisplayName("Should calculate trip duration for long trips")
    void testGetTripDurationMinutesForLongTrip() {
        LocalDateTime longPickup = LocalDateTime.of(2024, 1, 15, 8, 0, 0);
        LocalDateTime longDropoff = LocalDateTime.of(2024, 1, 15, 12, 30, 0);
        
        YellowTaxi longTrip = new YellowTaxi(
                1,
                longPickup,
                longDropoff,
                2,
                25.0,
                1,
                "N",
                100,
                200,
                1,
                50.00,
                1.00,
                0.50,
                10.00,
                5.00,
                0.30,
                66.80,
                2.50,
                0.00,
                0.00
        );
        
        Long duration = longTrip.getTripDurationMinutes();
        assertNotNull(duration);
        assertEquals(270L, duration); // 4.5 hours = 270 minutes
    }

    @Test
    @DisplayName("Should serialize YellowTaxi to JSON")
    void testSerializeToJson() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(validTaxi);
        assertNotNull(json);
        assertTrue(json.contains("VendorID"));
        assertTrue(json.contains("tpep_pickup_datetime"));
        assertTrue(json.contains("tpep_dropoff_datetime"));
        assertTrue(json.contains("airport_fee"));
        assertTrue(json.contains("1")); // vendorID
    }

    @Test
    @DisplayName("Should deserialize JSON to YellowTaxi")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = """
                {
                    "VendorID": 1,
                    "tpep_pickup_datetime": "2024-01-15T10:30:00",
                    "tpep_dropoff_datetime": "2024-01-15T11:15:00",
                    "passenger_count": 2,
                    "trip_distance": 5.5,
                    "RatecodeID": 1,
                    "store_and_fwd_flag": "N",
                    "PULocationID": 100,
                    "DOLocationID": 200,
                    "payment_type": 1,
                    "fare_amount": 15.50,
                    "extra": 1.00,
                    "mta_tax": 0.50,
                    "tip_amount": 3.00,
                    "tolls_amount": 0.00,
                    "improvement_surcharge": 0.30,
                    "total_amount": 20.30,
                    "congestion_surcharge": 2.50,
                    "airport_fee": 0.00,
                    "cbd_congestion_fee": 0.00
                }
                """;

        YellowTaxi taxi = objectMapper.readValue(json, YellowTaxi.class);
        
        assertNotNull(taxi);
        assertEquals(1, taxi.vendorID());
        assertEquals(pickupTime, taxi.tpepPickupDateTime());
        assertEquals(dropoffTime, taxi.tpepDropoffDateTime());
        assertEquals("N", taxi.storeAndFwdFlag());
        assertEquals(2, taxi.passengerCount());
        assertEquals(5.5, taxi.tripDistance());
        assertEquals(0.00, taxi.airportFee());
    }

    @Test
    @DisplayName("Should round-trip serialize and deserialize YellowTaxi")
    void testRoundTripJsonTransformation() throws JsonProcessingException {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(validTaxi);
        assertNotNull(json);
        
        // Deserialize back to object
        YellowTaxi deserialized = objectMapper.readValue(json, YellowTaxi.class);
        
        // Verify all fields match
        assertEquals(validTaxi.vendorID(), deserialized.vendorID());
        assertEquals(validTaxi.tpepPickupDateTime(), deserialized.tpepPickupDateTime());
        assertEquals(validTaxi.tpepDropoffDateTime(), deserialized.tpepDropoffDateTime());
        assertEquals(validTaxi.storeAndFwdFlag(), deserialized.storeAndFwdFlag());
        assertEquals(validTaxi.ratecodeID(), deserialized.ratecodeID());
        assertEquals(validTaxi.puLocationID(), deserialized.puLocationID());
        assertEquals(validTaxi.doLocationID(), deserialized.doLocationID());
        assertEquals(validTaxi.passengerCount(), deserialized.passengerCount());
        assertEquals(validTaxi.tripDistance(), deserialized.tripDistance());
        assertEquals(validTaxi.fareAmount(), deserialized.fareAmount());
        assertEquals(validTaxi.extra(), deserialized.extra());
        assertEquals(validTaxi.mtaTax(), deserialized.mtaTax());
        assertEquals(validTaxi.tipAmount(), deserialized.tipAmount());
        assertEquals(validTaxi.tollsAmount(), deserialized.tollsAmount());
        assertEquals(validTaxi.improvementSurcharge(), deserialized.improvementSurcharge());
        assertEquals(validTaxi.totalAmount(), deserialized.totalAmount());
        assertEquals(validTaxi.paymentType(), deserialized.paymentType());
        assertEquals(validTaxi.congestionSurcharge(), deserialized.congestionSurcharge());
        assertEquals(validTaxi.airportFee(), deserialized.airportFee());
        assertEquals(validTaxi.cbdCongestionFee(), deserialized.cbdCongestionFee());
    }

    @Test
    @DisplayName("Should handle JSON with airport fee")
    void testJsonWithAirportFee() throws JsonProcessingException {
        String json = """
                {
                    "VendorID": 2,
                    "tpep_pickup_datetime": "2024-01-15T14:00:00",
                    "tpep_dropoff_datetime": "2024-01-15T14:45:00",
                    "passenger_count": 3,
                    "trip_distance": 12.5,
                    "RatecodeID": 2,
                    "store_and_fwd_flag": "Y",
                    "PULocationID": 150,
                    "DOLocationID": 250,
                    "payment_type": 1,
                    "fare_amount": 35.00,
                    "extra": 2.00,
                    "mta_tax": 0.50,
                    "tip_amount": 7.00,
                    "tolls_amount": 5.50,
                    "improvement_surcharge": 0.30,
                    "total_amount": 50.30,
                    "congestion_surcharge": 2.50,
                    "airport_fee": 1.25,
                    "cbd_congestion_fee": 1.00
                }
                """;

        YellowTaxi taxi = objectMapper.readValue(json, YellowTaxi.class);
        
        assertNotNull(taxi);
        assertEquals(2, taxi.vendorID());
        assertEquals(2, taxi.ratecodeID()); // JFK
        assertEquals("Y", taxi.storeAndFwdFlag());
        assertEquals(3, taxi.passengerCount());
        assertEquals(1.25, taxi.airportFee());
        assertEquals(1.00, taxi.cbdCongestionFee());
        assertTrue(taxi.isValid());
    }
}

