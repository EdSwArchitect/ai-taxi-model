package com.bscllc.ai.text.model.datamodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bscllc.ai.text.model.datamodel.RatecodeID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the RatecodeID enum.
 */
@DisplayName("RatecodeID Tests")
class RatecodeIDTest {

    @Test
    @DisplayName("Should have correct code for Standard Rate")
    void testStandardRateCode() {
        assertEquals(1, RatecodeID.STANDARD_RATE.getCode());
        assertEquals("Standard rate", RatecodeID.STANDARD_RATE.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for JFK")
    void testJFKCode() {
        assertEquals(2, RatecodeID.JFK.getCode());
        assertEquals("JFK", RatecodeID.JFK.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Newark")
    void testNewarkCode() {
        assertEquals(3, RatecodeID.NEWARK.getCode());
        assertEquals("Newark", RatecodeID.NEWARK.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Nassau or Westchester")
    void testNassauOrWestchesterCode() {
        assertEquals(4, RatecodeID.NASSAU_OR_WESTCHESTER.getCode());
        assertEquals("Nassau or Westchester", RatecodeID.NASSAU_OR_WESTCHESTER.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Negotiated Fare")
    void testNegotiatedFareCode() {
        assertEquals(5, RatecodeID.NEGOTIATED_FARE.getCode());
        assertEquals("Negotiated fare", RatecodeID.NEGOTIATED_FARE.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Group Ride")
    void testGroupRideCode() {
        assertEquals(6, RatecodeID.GROUP_RIDE.getCode());
        assertEquals("Group ride", RatecodeID.GROUP_RIDE.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Null/Unknown")
    void testNullOrUnknownCode() {
        assertEquals(99, RatecodeID.NULL_OR_UNKNOWN.getCode());
        assertEquals("Null/unknown", RatecodeID.NULL_OR_UNKNOWN.getDescription());
    }

    @Test
    @DisplayName("Should convert code to RatecodeID enum")
    void testFromCode() {
        assertEquals(RatecodeID.STANDARD_RATE, RatecodeID.fromCode(1));
        assertEquals(RatecodeID.JFK, RatecodeID.fromCode(2));
        assertEquals(RatecodeID.NEWARK, RatecodeID.fromCode(3));
        assertEquals(RatecodeID.NASSAU_OR_WESTCHESTER, RatecodeID.fromCode(4));
        assertEquals(RatecodeID.NEGOTIATED_FARE, RatecodeID.fromCode(5));
        assertEquals(RatecodeID.GROUP_RIDE, RatecodeID.fromCode(6));
        assertEquals(RatecodeID.NULL_OR_UNKNOWN, RatecodeID.fromCode(99));
    }

    @Test
    @DisplayName("Should return null for invalid code")
    void testFromCodeWithInvalidCode() {
        assertNull(RatecodeID.fromCode(0));
        assertNull(RatecodeID.fromCode(7));
        assertNull(RatecodeID.fromCode(8));
        assertNull(RatecodeID.fromCode(50));
        assertNull(RatecodeID.fromCode(98));
        assertNull(RatecodeID.fromCode(100));
    }

    @Test
    @DisplayName("Should throw exception for invalid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithInvalidCode() {
        assertThrows(IllegalArgumentException.class, () -> RatecodeID.fromCodeOrThrow(0));
        assertThrows(IllegalArgumentException.class, () -> RatecodeID.fromCodeOrThrow(7));
        assertThrows(IllegalArgumentException.class, () -> RatecodeID.fromCodeOrThrow(98));
    }

    @Test
    @DisplayName("Should return correct RatecodeID for valid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithValidCode() {
        assertEquals(RatecodeID.STANDARD_RATE, RatecodeID.fromCodeOrThrow(1));
        assertEquals(RatecodeID.JFK, RatecodeID.fromCodeOrThrow(2));
        assertEquals(RatecodeID.NEWARK, RatecodeID.fromCodeOrThrow(3));
        assertEquals(RatecodeID.NASSAU_OR_WESTCHESTER, RatecodeID.fromCodeOrThrow(4));
        assertEquals(RatecodeID.NEGOTIATED_FARE, RatecodeID.fromCodeOrThrow(5));
        assertEquals(RatecodeID.GROUP_RIDE, RatecodeID.fromCodeOrThrow(6));
        assertEquals(RatecodeID.NULL_OR_UNKNOWN, RatecodeID.fromCodeOrThrow(99));
    }

    @Test
    @DisplayName("Should validate correct rate codes")
    void testIsValid() {
        assertTrue(RatecodeID.isValid(1));
        assertTrue(RatecodeID.isValid(2));
        assertTrue(RatecodeID.isValid(3));
        assertTrue(RatecodeID.isValid(4));
        assertTrue(RatecodeID.isValid(5));
        assertTrue(RatecodeID.isValid(6));
        assertTrue(RatecodeID.isValid(99));
    }

    @Test
    @DisplayName("Should invalidate incorrect rate codes")
    void testIsValidWithInvalidCodes() {
        assertFalse(RatecodeID.isValid(0));
        assertFalse(RatecodeID.isValid(7));
        assertFalse(RatecodeID.isValid(8));
        assertFalse(RatecodeID.isValid(50));
        assertFalse(RatecodeID.isValid(98));
        assertFalse(RatecodeID.isValid(100));
    }

    @Test
    @DisplayName("Should return all seven rate code IDs")
    void testAllRatecodeIDs() {
        RatecodeID[] allRateCodes = RatecodeID.values();
        assertEquals(7, allRateCodes.length);
        
        assertTrue(containsRateCode(allRateCodes, RatecodeID.STANDARD_RATE));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.JFK));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.NEWARK));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.NASSAU_OR_WESTCHESTER));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.NEGOTIATED_FARE));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.GROUP_RIDE));
        assertTrue(containsRateCode(allRateCodes, RatecodeID.NULL_OR_UNKNOWN));
    }

    @Test
    @DisplayName("Should have proper toString representation")
    void testToString() {
        String str = RatecodeID.STANDARD_RATE.toString();
        assertTrue(str.contains("code=1"));
        assertTrue(str.contains("Standard rate"));
        
        String str99 = RatecodeID.NULL_OR_UNKNOWN.toString();
        assertTrue(str99.contains("code=99"));
        assertTrue(str99.contains("Null/unknown"));
    }

    @Test
    @DisplayName("Should handle all valid codes from yellow taxi data")
    void testAllValidYellowTaxiRateCodes() {
        int[] validCodes = {1, 2, 3, 4, 5, 6, 99};
        
        for (int code : validCodes) {
            assertTrue(RatecodeID.isValid(code), "Code " + code + " should be valid");
            assertNotNull(RatecodeID.fromCode(code), "Code " + code + " should return a RatecodeID");
        }
    }

    @Test
    @DisplayName("Should handle airport rate codes")
    void testAirportRateCodes() {
        assertEquals(RatecodeID.JFK, RatecodeID.fromCode(2));
        assertEquals(RatecodeID.NEWARK, RatecodeID.fromCode(3));
        
        assertTrue(RatecodeID.JFK.getDescription().contains("JFK"));
        assertTrue(RatecodeID.NEWARK.getDescription().contains("Newark"));
    }

    private boolean containsRateCode(RatecodeID[] rateCodes, RatecodeID rateCode) {
        for (RatecodeID rc : rateCodes) {
            if (rc == rateCode) {
                return true;
            }
        }
        return false;
    }
}

