package com.bscllc.ai.text.model.datamodel;

/**
 * Enum representing the RatecodeID codes for NYC TLC taxi trip records.
 * Based on the data dictionary from:
 * https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_yellow.pdf
 * 
 * RatecodeID is the final rate code in effect at the end of the trip.
 */
public enum RatecodeID {
    /**
     * Standard rate
     */
    STANDARD_RATE(1, "Standard rate"),
    
    /**
     * JFK Airport rate
     */
    JFK(2, "JFK"),
    
    /**
     * Newark Airport rate
     */
    NEWARK(3, "Newark"),
    
    /**
     * Nassau or Westchester rate
     */
    NASSAU_OR_WESTCHESTER(4, "Nassau or Westchester"),
    
    /**
     * Negotiated fare
     */
    NEGOTIATED_FARE(5, "Negotiated fare"),
    
    /**
     * Group ride
     */
    GROUP_RIDE(6, "Group ride"),
    
    /**
     * Null/unknown rate code
     */
    NULL_OR_UNKNOWN(99, "Null/unknown");

    private final int code;
    private final String description;

    /**
     * Constructor for RatecodeID enum.
     *
     * @param code the numeric code for the rate
     * @param description the description of the rate code
     */
    RatecodeID(int code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Gets the numeric code for this rate code ID.
     *
     * @return the rate code
     */
    public int getCode() {
        return code;
    }

    /**
     * Gets the description of this rate code.
     *
     * @return the rate code description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the RatecodeID enum constant for the given numeric code.
     *
     * @param code the rate code to look up
     * @return the RatecodeID enum constant, or null if no matching code is found
     */
    public static RatecodeID fromCode(int code) {
        for (RatecodeID ratecodeID : values()) {
            if (ratecodeID.code == code) {
                return ratecodeID;
            }
        }
        return null;
    }

    /**
     * Returns the RatecodeID enum constant for the given numeric code.
     * Throws an exception if the code is not found.
     *
     * @param code the rate code to look up
     * @return the RatecodeID enum constant
     * @throws IllegalArgumentException if the code is not a valid rate code ID
     */
    public static RatecodeID fromCodeOrThrow(int code) {
        RatecodeID ratecodeID = fromCode(code);
        if (ratecodeID == null) {
            throw new IllegalArgumentException("Invalid RatecodeID code: " + code);
        }
        return ratecodeID;
    }

    /**
     * Checks if the given code is a valid rate code ID.
     *
     * @param code the rate code to check
     * @return true if the code is valid, false otherwise
     */
    public static boolean isValid(int code) {
        return fromCode(code) != null;
    }

    /**
     * Returns a string representation of this RatecodeID.
     *
     * @return a string in the format "RatecodeID{code=1, description='Standard rate'}"
     */
    @Override
    public String toString() {
        return String.format("RatecodeID{code=%d, description='%s'}", code, description);
    }
}

