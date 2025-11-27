package com.bscllc.ai.text.model.datamodel;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bscllc.ai.text.model.datamodel.VendorID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the VendorID enum.
 */
@DisplayName("VendorID Tests")
class VendorIDTest {

    @Test
    @DisplayName("Should have correct code for Creative Mobile Technologies")
    void testCreativeMobileTechnologiesCode() {
        assertEquals(1, VendorID.CREATIVE_MOBILE_TECHNOLOGIES.getCode());
        assertEquals("Creative Mobile Technologies, LLC", 
                     VendorID.CREATIVE_MOBILE_TECHNOLOGIES.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Curb Mobility")
    void testCurbMobilityCode() {
        assertEquals(2, VendorID.CURB_MOBILITY.getCode());
        assertEquals("Curb Mobility, LLC", VendorID.CURB_MOBILITY.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Myle Technologies")
    void testMyleTechnologiesCode() {
        assertEquals(6, VendorID.MYLE_TECHNOLOGIES.getCode());
        assertEquals("Myle Technologies Inc", VendorID.MYLE_TECHNOLOGIES.getDescription());
    }

    @Test
    @DisplayName("Should have correct code for Helix")
    void testHelixCode() {
        assertEquals(7, VendorID.HELIX.getCode());
        assertEquals("Helix", VendorID.HELIX.getDescription());
    }

    @Test
    @DisplayName("Should convert code to VendorID enum")
    void testFromCode() {
        assertEquals(VendorID.CREATIVE_MOBILE_TECHNOLOGIES, VendorID.fromCode(1));
        assertEquals(VendorID.CURB_MOBILITY, VendorID.fromCode(2));
        assertEquals(VendorID.MYLE_TECHNOLOGIES, VendorID.fromCode(6));
        assertEquals(VendorID.HELIX, VendorID.fromCode(7));
    }

    @Test
    @DisplayName("Should return null for invalid code")
    void testFromCodeWithInvalidCode() {
        assertNull(VendorID.fromCode(0));
        assertNull(VendorID.fromCode(3));
        assertNull(VendorID.fromCode(4));
        assertNull(VendorID.fromCode(5));
        assertNull(VendorID.fromCode(8));
        assertNull(VendorID.fromCode(99));
    }

    @Test
    @DisplayName("Should throw exception for invalid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithInvalidCode() {
        assertThrows(IllegalArgumentException.class, () -> VendorID.fromCodeOrThrow(0));
        assertThrows(IllegalArgumentException.class, () -> VendorID.fromCodeOrThrow(3));
        assertThrows(IllegalArgumentException.class, () -> VendorID.fromCodeOrThrow(99));
    }

    @Test
    @DisplayName("Should return correct VendorID for valid code in fromCodeOrThrow")
    void testFromCodeOrThrowWithValidCode() {
        assertEquals(VendorID.CREATIVE_MOBILE_TECHNOLOGIES, VendorID.fromCodeOrThrow(1));
        assertEquals(VendorID.CURB_MOBILITY, VendorID.fromCodeOrThrow(2));
        assertEquals(VendorID.MYLE_TECHNOLOGIES, VendorID.fromCodeOrThrow(6));
        assertEquals(VendorID.HELIX, VendorID.fromCodeOrThrow(7));
    }

    @Test
    @DisplayName("Should validate correct vendor codes")
    void testIsValid() {
        assertTrue(VendorID.isValid(1));
        assertTrue(VendorID.isValid(2));
        assertTrue(VendorID.isValid(6));
        assertTrue(VendorID.isValid(7));
    }

    @Test
    @DisplayName("Should invalidate incorrect vendor codes")
    void testIsValidWithInvalidCodes() {
        assertFalse(VendorID.isValid(0));
        assertFalse(VendorID.isValid(3));
        assertFalse(VendorID.isValid(4));
        assertFalse(VendorID.isValid(5));
        assertFalse(VendorID.isValid(8));
        assertFalse(VendorID.isValid(99));
    }

    @Test
    @DisplayName("Should return all four vendor IDs")
    void testAllVendorIDs() {
        VendorID[] allVendors = VendorID.values();
        assertEquals(4, allVendors.length);
        
        assertTrue(containsVendor(allVendors, VendorID.CREATIVE_MOBILE_TECHNOLOGIES));
        assertTrue(containsVendor(allVendors, VendorID.CURB_MOBILITY));
        assertTrue(containsVendor(allVendors, VendorID.MYLE_TECHNOLOGIES));
        assertTrue(containsVendor(allVendors, VendorID.HELIX));
    }

    @Test
    @DisplayName("Should have proper toString representation")
    void testToString() {
        String str = VendorID.CREATIVE_MOBILE_TECHNOLOGIES.toString();
        assertTrue(str.contains("code=1"));
        assertTrue(str.contains("Creative Mobile Technologies, LLC"));
    }

    @Test
    @DisplayName("Should handle all valid codes from yellow taxi data")
    void testAllValidYellowTaxiCodes() {
        int[] validCodes = {1, 2, 6, 7};
        
        for (int code : validCodes) {
            assertTrue(VendorID.isValid(code), "Code " + code + " should be valid");
            assertNotNull(VendorID.fromCode(code), "Code " + code + " should return a VendorID");
        }
    }

    private boolean containsVendor(VendorID[] vendors, VendorID vendor) {
        for (VendorID v : vendors) {
            if (v == vendor) {
                return true;
            }
        }
        return false;
    }
}

