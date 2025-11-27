package com.bscllc.ai.text.model.datamodel;

/**
 * Enum representing the VendorID codes for NYC TLC taxi trip records.
 * Based on the data dictionary from:
 * https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_yellow.pdf
 * 
 * VendorID is a code indicating the TPEP (Taxicab Passenger Enhancement Program) provider
 * that provided the record.
 */
public enum VendorID {
    /**
     * Creative Mobile Technologies, LLC
     */
    CREATIVE_MOBILE_TECHNOLOGIES(1, "Creative Mobile Technologies, LLC"),
    
    /**
     * Curb Mobility, LLC
     */
    CURB_MOBILITY(2, "Curb Mobility, LLC"),
    
    /**
     * Myle Technologies Inc
     */
    MYLE_TECHNOLOGIES(6, "Myle Technologies Inc"),
    
    /**
     * Helix
     */
    HELIX(7, "Helix");

    private final int code;
    private final String description;

    /**
     * Constructor for VendorID enum.
     *
     * @param code the numeric code for the vendor
     * @param description the description of the vendor
     */
    VendorID(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the numeric code for this vendor ID.
     *
     * @return the vendor code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the description of this vendor.
     *
     * @return the vendor description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the VendorID enum constant for the given numeric code.
     *
     * @param code the vendor code to look up
     * @return the VendorID enum constant, or null if no matching code is found
     */
    public static VendorID fromCode(int code) {
        for (VendorID vendorID : values()) {
            if (vendorID.code == code) {
                return vendorID;
            }
        }
        return null;
    }

    /**
     * Returns the VendorID enum constant for the given numeric code.
     * Throws an exception if the code is not found.
     *
     * @param code the vendor code to look up
     * @return the VendorID enum constant
     * @throws IllegalArgumentException if the code is not a valid vendor ID
     */
    public static VendorID fromCodeOrThrow(int code) {
        VendorID vendorID = fromCode(code);
        if (vendorID == null) {
            throw new IllegalArgumentException("Invalid VendorID code: " + code);
        }
        return vendorID;
    }

    /**
     * Checks if the given code is a valid vendor ID.
     *
     * @param code the vendor code to check
     * @return true if the code is valid, false otherwise
     */
    public static boolean isValid(int code) {
        return fromCode(code) != null;
    }

    /**
     * Returns a string representation of this VendorID.
     *
     * @return a string in the format "VendorID{code=1, description='Creative Mobile Technologies, LLC'}"
     */
    @Override
    public String toString() {
        return String.format("VendorID{code=%d, description='%s'}", code, description);
    }
}

