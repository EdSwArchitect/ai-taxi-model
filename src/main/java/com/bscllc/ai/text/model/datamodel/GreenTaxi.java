package com.bscllc.ai.text.model.datamodel;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Java record representing a Green Taxi (SHL) trip record from NYC TLC data.
 * Based on the data dictionary from:
 * https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_green.pdf
 *
 * @param vendorID A code indicating the LPEP provider that provided the record.
 *                 1 = Creative Mobile Technologies, LLC
 *                 2 = Curb Mobility, LLC
 *                 6 = Myle Technologies Inc
 * @param lpepPickupDateTime The date and time when the meter was engaged.
 * @param lpepDropoffDateTime The date and time when the meter was disengaged.
 * @param storeAndFwdFlag This flag indicates whether the trip record was held in vehicle memory
 *                        before sending to the vendor, aka "store and forward," because the vehicle
 *                        did not have a connection to the server.
 *                        Y = store and forward trip
 *                        N = not a store and forward trip
 * @param ratecodeID The final rate code in effect at the end of the trip.
 *                   1 = Standard rate
 *                   2 = JFK
 *                   3 = Newark
 *                   4 = Nassau or Westchester
 *                   5 = Negotiated fare
 *                   6 = Group ride
 *                   99 = Null/unknown
 * @param puLocationID TLC Taxi Zone in which the taximeter was engaged.
 * @param doLocationID TLC Taxi Zone in which the taximeter was disengaged.
 * @param passengerCount The number of passengers in the vehicle.
 * @param tripDistance The elapsed trip distance in miles reported by the taximeter.
 * @param fareAmount The time-and-distance fare calculated by the meter.
 * @param extra Miscellaneous extras and surcharges.
 * @param mtaTax Tax that is automatically triggered based on the metered rate in use.
 * @param tipAmount Tip amount - This field is automatically populated for credit card tips.
 *                  Cash tips are not included.
 * @param tollsAmount Total amount of all tolls paid in trip.
 * @param improvementSurcharge Improvement surcharge assessed trips at the flag drop.
 *                             The improvement surcharge began being levied in 2015.
 * @param totalAmount The total amount charged to passengers. Does not include cash tips.
 * @param paymentType A numeric code signifying how the passenger paid for the trip.
 *                   0 = Flex Fare trip
 *                   1 = Credit card
 *                   2 = Cash
 *                   3 = No charge
 *                   4 = Dispute
 *                   5 = Unknown
 *                   6 = Voided trip
 * @param tripType A code indicating whether the trip was a street-hail or a dispatch that is
 *                 automatically assigned based on the metered rate in use but can be altered by the driver.
 *                 1 = Street-hail
 *                 2 = Dispatch
 * @param congestionSurcharge Total amount collected in trip for NYS congestion surcharge.
 * @param cbdCongestionFee Per-trip charge for MTA's Congestion Relief Zone starting Jan. 5, 2025.
 */
public record GreenTaxi(
        @JsonProperty("VendorID")
        Integer vendorID,
        
        @JsonProperty("lpep_pickup_datetime")
        LocalDateTime lpepPickupDateTime,
        
        @JsonProperty("lpep_dropoff_datetime")
        LocalDateTime lpepDropoffDateTime,
        
        @JsonProperty("store_and_fwd_flag")
        String storeAndFwdFlag,
        
        @JsonProperty("RatecodeID")
        Integer ratecodeID,
        
        @JsonProperty("PULocationID")
        Integer puLocationID,
        
        @JsonProperty("DOLocationID")
        Integer doLocationID,
        
        @JsonProperty("passenger_count")
        Integer passengerCount,
        
        @JsonProperty("trip_distance")
        Double tripDistance,
        
        @JsonProperty("fare_amount")
        Double fareAmount,
        
        @JsonProperty("extra")
        Double extra,
        
        @JsonProperty("mta_tax")
        Double mtaTax,
        
        @JsonProperty("tip_amount")
        Double tipAmount,
        
        @JsonProperty("tolls_amount")
        Double tollsAmount,
        
        @JsonProperty("improvement_surcharge")
        Double improvementSurcharge,
        
        @JsonProperty("total_amount")
        Double totalAmount,
        
        @JsonProperty("payment_type")
        Integer paymentType,
        
        @JsonProperty("trip_type")
        Integer tripType,
        
        @JsonProperty("congestion_surcharge")
        Double congestionSurcharge,
        
        @JsonProperty("cbd_congestion_fee")
        Double cbdCongestionFee
) {
    /**
     * Validates the GreenTaxi record.
     *
     * @return true if the record is valid, false otherwise
     */
    public boolean isValid() {
        // Basic validation checks
        if (vendorID == null || (vendorID != 1 && vendorID != 2 && vendorID != 6)) {
            return false;
        }
        if (lpepPickupDateTime == null || lpepDropoffDateTime == null) {
            return false;
        }
        if (lpepDropoffDateTime.isBefore(lpepPickupDateTime)) {
            return false;
        }
        if (storeAndFwdFlag == null || (!storeAndFwdFlag.equals("Y") && !storeAndFwdFlag.equals("N"))) {
            return false;
        }
        if (passengerCount != null && passengerCount < 0) {
            return false;
        }
        if (tripDistance != null && tripDistance < 0) {
            return false;
        }
        if (tripType != null && tripType != 1 && tripType != 2) {
            return false;
        }
        return true;
    }

    /**
     * Calculates the trip duration in minutes.
     *
     * @return the trip duration in minutes, or null if dates are invalid
     */
    public Long getTripDurationMinutes() {
        if (lpepPickupDateTime == null || lpepDropoffDateTime == null) {
            return null;
        }
        return java.time.Duration.between(lpepPickupDateTime, lpepDropoffDateTime).toMinutes();
    }
}

