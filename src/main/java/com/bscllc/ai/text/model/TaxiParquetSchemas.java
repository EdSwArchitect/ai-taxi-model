package com.bscllc.ai.text.model;

/**
 * Constants containing the Parquet schema definitions in JSON format
 * for Yellow and Green taxi trip data files.
 */
public final class TaxiParquetSchemas {

    private TaxiParquetSchemas() {
        // Utility class - prevent instantiation
    }

    /**
     * JSON schema for Yellow taxi trip data Parquet files.
     */
    public static final String YELLOW = """
        {
          "type" : "record",
          "name" : "schema",
          "fields" : [ {
            "name" : "VendorID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "tpep_pickup_datetime",
            "type" : [ "null", {
              "type" : "long",
              "logicalType" : "local-timestamp-micros"
            } ],
            "default" : null
          }, {
            "name" : "tpep_dropoff_datetime",
            "type" : [ "null", {
              "type" : "long",
              "logicalType" : "local-timestamp-micros"
            } ],
            "default" : null
          }, {
            "name" : "passenger_count",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "trip_distance",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "RatecodeID",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "store_and_fwd_flag",
            "type" : [ "null", "string" ],
            "default" : null
          }, {
            "name" : "PULocationID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "DOLocationID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "payment_type",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "fare_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "extra",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "mta_tax",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "tip_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "tolls_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "improvement_surcharge",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "total_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "congestion_surcharge",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "Airport_fee",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "cbd_congestion_fee",
            "type" : [ "null", "double" ],
            "default" : null
          } ]
        }
        """;

    /**
     * JSON schema for Green taxi trip data Parquet files.
     */
    public static final String GREEN = """
        {
          "type" : "record",
          "name" : "schema",
          "fields" : [ {
            "name" : "VendorID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "lpep_pickup_datetime",
            "type" : [ "null", {
              "type" : "long",
              "logicalType" : "local-timestamp-micros"
            } ],
            "default" : null
          }, {
            "name" : "lpep_dropoff_datetime",
            "type" : [ "null", {
              "type" : "long",
              "logicalType" : "local-timestamp-micros"
            } ],
            "default" : null
          }, {
            "name" : "store_and_fwd_flag",
            "type" : [ "null", "string" ],
            "default" : null
          }, {
            "name" : "RatecodeID",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "PULocationID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "DOLocationID",
            "type" : [ "null", "int" ],
            "default" : null
          }, {
            "name" : "passenger_count",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "trip_distance",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "fare_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "extra",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "mta_tax",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "tip_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "tolls_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "ehail_fee",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "improvement_surcharge",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "total_amount",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "payment_type",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "trip_type",
            "type" : [ "null", "long" ],
            "default" : null
          }, {
            "name" : "congestion_surcharge",
            "type" : [ "null", "double" ],
            "default" : null
          }, {
            "name" : "cbd_congestion_fee",
            "type" : [ "null", "double" ],
            "default" : null
          } ]
        }
        """;
}

