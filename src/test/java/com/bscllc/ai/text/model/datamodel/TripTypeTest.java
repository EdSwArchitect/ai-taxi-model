package com.bscllc.ai.text.model.datamodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bscllc.ai.text.model.datamodel.TripType;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TripType enum.
 */
@DisplayName("TripType Tests")
class TripTypeTest {

    @Test
    @DisplayName("Should have correct code for Street-hail")
    void testStreetHailCode() {
        assertEquals(1, TripType.STREET_HAIL.getCode());
        assertEquals("Street-hail", TripType.STREET_HAIL.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Dispatch")
    void testDispatchCode() {
        assertEquals(2, TripType.DISPATCH.getCode());
        assertEquals("Dispatch", TripType.DISPATCH.getDescription());
    }

    @Test
    @DisplayName("Should convert code to TripType enum")
    void testFromCode() {
        assertEquals(TripType.STREET_HAIL, TripType.fromCode(1));
        assertEquals(TripType.DISPATCH, TripType.fromCode(2));
    }

    @Test
    @DisplayName("Should return null for invalid code")
    void testFromCodeWithInvalidCode() {
        assertNull(TripType.fromCode(0));
        assertNull(TripType.fromCode(3));
        assertNull(TripType.fromCode(4));
        assertNull(TripType.fromCode(99));
    }

    @Test
    @DisplayName("Should throw exception for invalid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithInvalidCode() {
        assertThrows(IllegalArgumentException.class, () -> TripType.fromCodeOrThrow(0));
        assertThrows(IllegalArgumentException.class, () -> TripType.fromCodeOrThrow(3));
        assertThrows(IllegalArgumentException.class, () -> TripType.fromCodeOrThrow(99));
    }

    @Test
    @DisplayName("Should return correct TripType for valid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithValidCode() {
        assertEquals(TripType.STREET_HAIL, TripType.fromCodeOrThrow(1));
        assertEquals(TripType.DISPATCH, TripType.fromCodeOrThrow(2));
    }

    @Test
    @DisplayName("Should validate correct trip type codes")
    void testIsValid() {
        assertTrue(TripType.isValid(1));
        assertTrue(TripType.isValid(2));
    }

    @Test
    @DisplayName("Should invalidate incorrect trip type codes")
    void testIsValidWithInvalidCodes() {
        assertFalse(TripType.isValid(0));
        assertFalse(TripType.isValid(3));
        assertFalse(TripType.isValid(4));
        assertFalse(TripType.isValid(99));
    }

    @Test
    @DisplayName("Should return both trip type IDs")
    void testAllTripTypes() {
        TripType[] allTripTypes = TripType.values();
        assertEquals(2, allTripTypes.length);
        
        assertTrue(containsTripType(allTripTypes, TripType.STREET_HAIL));
        assertTrue(containsTripType(allTripTypes, TripType.DISPATCH));
    }

    @Test
    @DisplayName("Should have proper toString representation")
    void testToString() {
        String str = TripType.STREET_HAIL.toString();
        assertTrue(str.contains("code=1"));
        assertTrue(str.contains("Street-hail"));
        
        String str2 = TripType.DISPATCH.toString();
        assertTrue(str2.contains("code=2"));
        assertTrue(str2.contains("Dispatch"));
    }

    @Test
    @DisplayName("Should handle all valid codes from green taxi data")
    void testAllValidGreenTaxiTripTypes() {
        int[] validCodes = {1, 2};
        
        for (int code : validCodes) {
            assertTrue(TripType.isValid(code), "Code " + code + " should be valid");
            assertNotNull(TripType.fromCode(code), "Code " + code + " should return a TripType");
        }
    }

    @Test
    @DisplayName("Should distinguish between Street-hail and Dispatch")
    void testStreetHailVsDispatch() {
        TripType streetHail = TripType.fromCode(1);
        TripType dispatch = TripType.fromCode(2);
        
        assertNotEquals(streetHail, dispatch);
        assertEquals(TripType.STREET_HAIL, streetHail);
        assertEquals(TripType.DISPATCH, dispatch);
    }

    @Test
    @DisplayName("Should handle Street-hail specific characteristics")
    void testStreetHailCharacteristics() {
        TripType streetHail = TripType.STREET_HAIL;
        assertEquals(1, streetHail.getCode());
        assertTrue(streetHail.getDescription().contains("Street"));
        assertTrue(streetHail.getDescription().contains("hail"));
    }

    @Test
    @DisplayName("Should handle Dispatch specific characteristics")
    void testDispatchCharacteristics() {
        TripType dispatch = TripType.DISPATCH;
        assertEquals(2, dispatch.getCode());
        assertEquals("Dispatch", dispatch.getDescription());
    }

    private boolean containsTripType(TripType[] tripTypes, TripType tripType) {
        for (TripType tt : tripTypes) {
            if (tt == tripType) {
                return true;
            }
        }
        return false;
    }
}

