package com.bscllc.ai.text.model.datamodel;

import com.bscllc.ai.text.model.datamodel.GreenTaxi;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the GreenTaxi record.
 */
@DisplayName("GreenTaxi Tests")
class GreenTaxiTest {

    private GreenTaxi validTaxi;
    private LocalDateTime pickupTime;
    private LocalDateTime dropoffTime;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        pickupTime = LocalDateTime.of(2024, 1, 15, 10, 30, 0);
        dropoffTime = LocalDateTime.of(2024, 1, 15, 11, 15, 0);
        
        validTaxi = new GreenTaxi(
                1, // vendorID
                pickupTime, // lpepPickupDateTime
                dropoffTime, // lpepDropoffDateTime
                "N", // storeAndFwdFlag
                1, // ratecodeID
                100, // puLocationID
                200, // doLocationID
                2, // passengerCount
                5.5, // tripDistance
                15.50, // fareAmount
                1.00, // extra
                0.50, // mtaTax
                3.00, // tipAmount
                0.00, // tollsAmount
                0.30, // improvementSurcharge
                20.30, // totalAmount
                1, // paymentType
                1, // tripType (Street-hail)
                2.50, // congestionSurcharge
                0.00 // cbdCongestionFee
        );

        // Configure ObjectMapper for JSON transformation
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Test
    @DisplayName("Should create a valid GreenTaxi record")
    void testCreateValidGreenTaxi() {
        assertNotNull(validTaxi);
        assertEquals(1, validTaxi.vendorID());
        assertEquals(pickupTime, validTaxi.lpepPickupDateTime());
        assertEquals(dropoffTime, validTaxi.lpepDropoffDateTime());
        assertEquals(2, validTaxi.passengerCount());
        assertEquals(5.5, validTaxi.tripDistance());
        assertEquals(1, validTaxi.tripType());
    }

    @Test
    @DisplayName("Should validate a correct GreenTaxi record")
    void testIsValidWithValidRecord() {
        assertTrue(validTaxi.isValid());
    }

    @Test
    @DisplayName("Should fail validation with invalid vendorID")
    void testIsValidWithInvalidVendorID() {
        GreenTaxi invalidVendor = new GreenTaxi(
                99, // invalid vendorID
                pickupTime,
                dropoffTime,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertFalse(invalidVendor.isValid());
    }

    @Test
    @DisplayName("Should fail validation with null pickup datetime")
    void testIsValidWithNullPickupDateTime() {
        GreenTaxi invalidDateTime = new GreenTaxi(
                1,
                null, // null pickup time
                dropoffTime,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertFalse(invalidDateTime.isValid());
    }

    @Test
    @DisplayName("Should fail validation when dropoff is before pickup")
    void testIsValidWithDropoffBeforePickup() {
        GreenTaxi invalidOrder = new GreenTaxi(
                1,
                dropoffTime, // pickup after dropoff
                pickupTime, // dropoff before pickup
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertFalse(invalidOrder.isValid());
    }

    @Test
    @DisplayName("Should fail validation with invalid storeAndFwdFlag")
    void testIsValidWithInvalidStoreAndFwdFlag() {
        GreenTaxi invalidFlag = new GreenTaxi(
                1,
                pickupTime,
                dropoffTime,
                "X", // invalid flag
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertFalse(invalidFlag.isValid());
    }

    @Test
    @DisplayName("Should fail validation with invalid tripType")
    void testIsValidWithInvalidTripType() {
        GreenTaxi invalidTripType = new GreenTaxi(
                1,
                pickupTime,
                dropoffTime,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                99, // invalid tripType
                2.50,
                0.00
        );
        assertFalse(invalidTripType.isValid());
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
        GreenTaxi nullDates = new GreenTaxi(
                1,
                null,
                null,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertNull(nullDates.getTripDurationMinutes());
    }

    @Test
    @DisplayName("Should handle all valid vendor IDs")
    void testAllValidVendorIDs() {
        int[] validVendorIDs = {1, 2, 6};
        
        for (int vendorID : validVendorIDs) {
            GreenTaxi taxi = new GreenTaxi(
                    vendorID,
                    pickupTime,
                    dropoffTime,
                    "N",
                    1,
                    100,
                    200,
                    2,
                    5.5,
                    15.50,
                    1.00,
                    0.50,
                    3.00,
                    0.00,
                    0.30,
                    20.30,
                    1,
                    1,
                    2.50,
                    0.00
            );
            assertTrue(taxi.isValid(), "VendorID " + vendorID + " should be valid");
        }
    }

    @Test
    @DisplayName("Should handle store and forward flag Y")
    void testStoreAndForwardFlagY() {
        GreenTaxi storeForward = new GreenTaxi(
                1,
                pickupTime,
                dropoffTime,
                "Y", // store and forward
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1,
                2.50,
                0.00
        );
        assertTrue(storeForward.isValid());
        assertEquals("Y", storeForward.storeAndFwdFlag());
    }

    @Test
    @DisplayName("Should handle trip type Street-hail (1)")
    void testTripTypeStreetHail() {
        GreenTaxi streetHail = new GreenTaxi(
                1,
                pickupTime,
                dropoffTime,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                1, // Street-hail
                2.50,
                0.00
        );
        assertTrue(streetHail.isValid());
        assertEquals(1, streetHail.tripType());
    }

    @Test
    @DisplayName("Should handle trip type Dispatch (2)")
    void testTripTypeDispatch() {
        GreenTaxi dispatch = new GreenTaxi(
                1,
                pickupTime,
                dropoffTime,
                "N",
                1,
                100,
                200,
                2,
                5.5,
                15.50,
                1.00,
                0.50,
                3.00,
                0.00,
                0.30,
                20.30,
                1,
                2, // Dispatch
                2.50,
                0.00
        );
        assertTrue(dispatch.isValid());
        assertEquals(2, dispatch.tripType());
    }

    @Test
    @DisplayName("Should serialize GreenTaxi to JSON")
    void testSerializeToJson() throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(validTaxi);
        assertNotNull(json);
        assertTrue(json.contains("VendorID"));
        assertTrue(json.contains("lpep_pickup_datetime"));
        assertTrue(json.contains("lpep_dropoff_datetime"));
        assertTrue(json.contains("trip_type"));
        assertTrue(json.contains("1")); // vendorID
    }

    @Test
    @DisplayName("Should deserialize JSON to GreenTaxi")
    void testDeserializeFromJson() throws JsonProcessingException {
        String json = """
                {
                    "VendorID": 1,
                    "lpep_pickup_datetime": "2024-01-15T10:30:00",
                    "lpep_dropoff_datetime": "2024-01-15T11:15:00",
                    "store_and_fwd_flag": "N",
                    "RatecodeID": 1,
                    "PULocationID": 100,
                    "DOLocationID": 200,
                    "passenger_count": 2,
                    "trip_distance": 5.5,
                    "fare_amount": 15.50,
                    "extra": 1.00,
                    "mta_tax": 0.50,
                    "tip_amount": 3.00,
                    "tolls_amount": 0.00,
                    "improvement_surcharge": 0.30,
                    "total_amount": 20.30,
                    "payment_type": 1,
                    "trip_type": 1,
                    "congestion_surcharge": 2.50,
                    "cbd_congestion_fee": 0.00
                }
                """;

        GreenTaxi taxi = objectMapper.readValue(json, GreenTaxi.class);
        
        assertNotNull(taxi);
        assertEquals(1, taxi.vendorID());
        assertEquals(pickupTime, taxi.lpepPickupDateTime());
        assertEquals(dropoffTime, taxi.lpepDropoffDateTime());
        assertEquals("N", taxi.storeAndFwdFlag());
        assertEquals(1, taxi.tripType());
        assertEquals(2, taxi.passengerCount());
        assertEquals(5.5, taxi.tripDistance());
    }

    @Test
    @DisplayName("Should round-trip serialize and deserialize GreenTaxi")
    void testRoundTripJsonTransformation() throws JsonProcessingException {
        // Serialize to JSON
        String json = objectMapper.writeValueAsString(validTaxi);
        assertNotNull(json);
        
        // Deserialize back to object
        GreenTaxi deserialized = objectMapper.readValue(json, GreenTaxi.class);
        
        // Verify all fields match
        assertEquals(validTaxi.vendorID(), deserialized.vendorID());
        assertEquals(validTaxi.lpepPickupDateTime(), deserialized.lpepPickupDateTime());
        assertEquals(validTaxi.lpepDropoffDateTime(), deserialized.lpepDropoffDateTime());
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
        assertEquals(validTaxi.tripType(), deserialized.tripType());
        assertEquals(validTaxi.congestionSurcharge(), deserialized.congestionSurcharge());
        assertEquals(validTaxi.cbdCongestionFee(), deserialized.cbdCongestionFee());
    }

    @Test
    @DisplayName("Should handle JSON with Dispatch trip type")
    void testJsonWithDispatchTripType() throws JsonProcessingException {
        String json = """
                {
                    "VendorID": 2,
                    "lpep_pickup_datetime": "2024-01-15T14:00:00",
                    "lpep_dropoff_datetime": "2024-01-15T14:45:00",
                    "store_and_fwd_flag": "Y",
                    "RatecodeID": 2,
                    "PULocationID": 150,
                    "DOLocationID": 250,
                    "passenger_count": 3,
                    "trip_distance": 12.5,
                    "fare_amount": 35.00,
                    "extra": 2.00,
                    "mta_tax": 0.50,
                    "tip_amount": 7.00,
                    "tolls_amount": 5.50,
                    "improvement_surcharge": 0.30,
                    "total_amount": 50.30,
                    "payment_type": 1,
                    "trip_type": 2,
                    "congestion_surcharge": 2.50,
                    "cbd_congestion_fee": 1.00
                }
                """;

        GreenTaxi taxi = objectMapper.readValue(json, GreenTaxi.class);
        
        assertNotNull(taxi);
        assertEquals(2, taxi.vendorID());
        assertEquals(2, taxi.tripType()); // Dispatch
        assertEquals("Y", taxi.storeAndFwdFlag());
        assertEquals(3, taxi.passengerCount());
        assertTrue(taxi.isValid());
    }
}

