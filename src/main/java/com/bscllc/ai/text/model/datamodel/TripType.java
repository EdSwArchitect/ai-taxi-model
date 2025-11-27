package com.bscllc.ai.text.model.datamodel;

/**
 * Enum representing the TripType codes for NYC TLC green taxi (SHL) trip records.
 * Based on the data dictionary from:
 * https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_green.pdf
 * 
 * TripType is a code indicating whether the trip was a street-hail or a dispatch
 * that is automatically assigned based on the metered rate in use but can be altered by the driver.
 */
public enum TripType {
    /**
     * Street-hail trip
     */
    STREET_HAIL(1, "Street-hail"),
    
    /**
     * Dispatch trip
     */
    DISPATCH(2, "Dispatch");

    private final int code;
    private final String description;

    /**
     * Constructor for TripType enum.
     *
     * @param code the numeric code for the trip type
     * @param description the description of the trip type
     */
    TripType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the numeric code for this trip type.
     *
     * @return the trip type code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the description of this trip type.
     *
     * @return the trip type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the TripType enum constant for the given numeric code.
     *
     * @param code the trip type code to look up
     * @return the TripType enum constant, or null if no matching code is found
     */
    public static TripType fromCode(int code) {
        for (TripType tripType : values()) {
            if (tripType.code == code) {
                return tripType;
            }
        }
        return null;
    }

    /**
     * Returns the TripType enum constant for the given numeric code.
     * Throws an exception if the code is not found.
     *
     * @param code the trip type code to look up
     * @return the TripType enum constant
     * @throws IllegalArgumentException if the code is not a valid trip type
     */
    public static TripType fromCodeOrThrow(int code) {
        TripType tripType = fromCode(code);
        if (tripType == null) {
            throw new IllegalArgumentException("Invalid TripType code: " + code);
        }
        return tripType;
    }

    /**
     * Checks if the given code is a valid trip type.
     *
     * @param code the trip type code to check
     * @return true if the code is valid, false otherwise
     */
    public static boolean isValid(int code) {
        return fromCode(code) != null;
    }

    /**
     * Returns a string representation of this TripType.
     *
     * @return a string in the format "TripType{code=1, description='Street-hail'}"
     */
    @Override
    public String toString() {
        return String.format("TripType{code=%d, description='%s'}", code, description);
    }
}

