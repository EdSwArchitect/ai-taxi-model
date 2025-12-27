# GeoMesa Simple Feature Type (SFT) Files for Taxi Data

This directory contains GeoMesa Simple Feature Type (SFT) configuration files that match the Yellow and Green Taxi Parquet schemas.

## Files

### Green Taxi SFT Files

#### `green-taxi.sft`
Default SFT configuration with Point geometry (properties format). Use this for representing green taxi trips as points (pickup or dropoff locations).

#### `green-taxi-point.sft`
Point geometry SFT with additional configuration for feature expiration (30 days). Use this when you want to automatically expire old features.

#### `green-taxi-linestring.sft`
LineString geometry SFT for representing trip routes (properties format). Use this when you have actual route coordinates (e.g., from GPS tracking) and want to visualize trip paths.

#### `green-taxi-compact.sft`
Compact string format SFT with Point geometry. Single-line format that's easier to use in command-line tools and code.

#### `green-taxi-linestring-compact.sft`
Compact string format SFT with LineString geometry. Single-line format for trip routes.

### Yellow Taxi SFT Files

#### `yellow-taxi.sft`
Default SFT configuration with Point geometry (properties format). Use this for representing yellow taxi trips as points (pickup or dropoff locations).

#### `yellow-taxi-point.sft`
Point geometry SFT with additional configuration for feature expiration (30 days). Use this when you want to automatically expire old features.

#### `yellow-taxi-linestring.sft`
LineString geometry SFT for representing trip routes (properties format). Use this when you have actual route coordinates (e.g., from GPS tracking) and want to visualize trip paths.

#### `yellow-taxi-compact.sft`
Compact string format SFT with Point geometry. Single-line format that's easier to use in command-line tools and code.

#### `yellow-taxi-linestring-compact.sft`
Compact string format SFT with LineString geometry. Single-line format for trip routes.

## Schema Fields

### Green Taxi Schema

All green taxi SFT files include the following fields matching the Green Taxi Parquet schema:

| Field Name | Type | Indexed | Description |
|------------|------|---------|-------------|
| `geom` | Point/LineString | Yes | Geometry field (SRID 4326) |
| `VendorID` | Integer | Yes | Vendor identifier (1, 2, or 6) |
| `lpep_pickup_datetime` | Date | Yes | Pickup date/time (default time field) |
| `lpep_dropoff_datetime` | Date | Yes | Dropoff date/time |
| `store_and_fwd_flag` | String | No | Store and forward flag (Y/N) |
| `RatecodeID` | Long | No | Rate code (1-6, 99) |
| `PULocationID` | Integer | Yes | Pickup location ID (Taxi Zone) |
| `DOLocationID` | Integer | Yes | Dropoff location ID (Taxi Zone) |
| `passenger_count` | Long | No | Number of passengers |
| `trip_distance` | Double | Yes | Trip distance in miles |
| `fare_amount` | Double | Yes | Base fare amount |
| `extra` | Double | No | Extra charges |
| `mta_tax` | Double | No | MTA tax |
| `tip_amount` | Double | Yes | Tip amount |
| `tolls_amount` | Double | No | Tolls amount |
| `ehail_fee` | Double | No | E-hail fee |
| `improvement_surcharge` | Double | No | Improvement surcharge |
| `total_amount` | Double | Yes | Total amount charged |
| `payment_type` | Long | Yes | Payment type (0-6) |
| `trip_type` | Long | Yes | Trip type (1=Street-hail, 2=Dispatch) |
| `congestion_surcharge` | Double | No | Congestion surcharge |
| `cbd_congestion_fee` | Double | No | CBD congestion fee |

### Yellow Taxi Schema

All yellow taxi SFT files include the following fields matching the Yellow Taxi Parquet schema:

| Field Name | Type | Indexed | Description |
|------------|------|---------|-------------|
| `geom` | Point/LineString | Yes | Geometry field (SRID 4326) |
| `VendorID` | Integer | Yes | Vendor identifier (1, 2, 6, or 7) |
| `tpep_pickup_datetime` | Date | Yes | Pickup date/time (default time field) |
| `tpep_dropoff_datetime` | Date | Yes | Dropoff date/time |
| `passenger_count` | Long | No | Number of passengers |
| `trip_distance` | Double | Yes | Trip distance in miles |
| `RatecodeID` | Long | No | Rate code (1-6, 99) |
| `store_and_fwd_flag` | String | No | Store and forward flag (Y/N) |
| `PULocationID` | Integer | Yes | Pickup location ID (Taxi Zone) |
| `DOLocationID` | Integer | Yes | Dropoff location ID (Taxi Zone) |
| `payment_type` | Long | Yes | Payment type (0-6) |
| `fare_amount` | Double | Yes | Base fare amount |
| `extra` | Double | No | Extra charges |
| `mta_tax` | Double | No | MTA tax |
| `tip_amount` | Double | Yes | Tip amount |
| `tolls_amount` | Double | No | Tolls amount |
| `improvement_surcharge` | Double | No | Improvement surcharge |
| `total_amount` | Double | Yes | Total amount charged |
| `congestion_surcharge` | Double | No | Congestion surcharge |
| `Airport_fee` | Double | No | Airport fee (LaGuardia/JFK) |
| `cbd_congestion_fee` | Double | No | CBD congestion fee |

**Key Differences from Green Taxi:**
- Uses `tpep_pickup_datetime`/`tpep_dropoff_datetime` instead of `lpep_*`
- Has `Airport_fee` instead of `ehail_fee`
- Does NOT have `trip_type` field
- VendorID can be 1, 2, 6, or 7 (green only has 1, 2, 6)

## Usage

### Creating a SimpleFeatureType from SFT file

```scala
import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes

val sft = SimpleFeatureTypes.createType(
  "green-taxi",
  readSftFile("green-taxi.sft")
)
```

### Using with GeoMesa Command Line Tools

**Properties format:**
```bash
# Create a catalog with the SFT
geomesa-hbase create-schema -c green-taxi -s green-taxi.sft

# Ingest data
geomesa-hbase ingest -c green-taxi -s green-taxi -C green-taxi.sft -i <input-file>
```

**Compact format:**
```bash
# Create a catalog with the compact SFT
geomesa-hbase create-schema -c green-taxi -s "$(cat green-taxi-compact.sft)"

# Or directly specify the SFT string
geomesa-hbase create-schema -c green-taxi -s "geom:Point:srid=4326,*VendorID:Integer:index=true,..."
```

### Using with GeoMesa Java API

**Properties format:**
```java
import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes;

// Read SFT from file
String sftSpec = Files.readString(Paths.get("green-taxi.sft"));
SimpleFeatureType sft = SimpleFeatureTypes.createType("green-taxi", sftSpec);

// Create data store
Map<String, String> params = new HashMap<>();
params.put("hbase.catalog", "green-taxi");
DataStore ds = DataStoreFinder.getDataStore(params);

// Create schema
ds.createSchema(sft);
```

**Compact format:**
```java
import org.locationtech.geomesa.utils.interop.SimpleFeatureTypes;

// Read compact SFT from file
String sftSpec = Files.readString(Paths.get("green-taxi-compact.sft")).trim();
SimpleFeatureType sft = SimpleFeatureTypes.createType("green-taxi", sftSpec);

// Or use directly
String sftSpec = "geom:Point:srid=4326,*VendorID:Integer:index=true,...";
SimpleFeatureType sft = SimpleFeatureTypes.createType("green-taxi", sftSpec);
```

## Configuration Options

### Indexed Fields
Fields marked with `index = true` are indexed for faster queries:

**Green Taxi:**
- `VendorID` - Filter by vendor
- `lpep_pickup_datetime` - Time-based queries (also used as default time field)
- `lpep_dropoff_datetime` - Filter by dropoff time
- `PULocationID` - Filter by pickup location
- `DOLocationID` - Filter by dropoff location
- `trip_distance` - Filter by distance range
- `fare_amount` - Filter by fare range
- `tip_amount` - Filter by tip amount
- `total_amount` - Filter by total amount
- `payment_type` - Filter by payment method
- `trip_type` - Filter by trip type (green only)

**Yellow Taxi:**
- `VendorID` - Filter by vendor
- `tpep_pickup_datetime` - Time-based queries (also used as default time field)
- `tpep_dropoff_datetime` - Filter by dropoff time
- `PULocationID` - Filter by pickup location
- `DOLocationID` - Filter by dropoff location
- `trip_distance` - Filter by distance range
- `fare_amount` - Filter by fare range
- `tip_amount` - Filter by tip amount
- `total_amount` - Filter by total amount
- `payment_type` - Filter by payment method

### User Data Options

- `geomesa.index.dtg` - Default time field for temporal indexing
  - Green Taxi: `lpep_pickup_datetime`
  - Yellow Taxi: `tpep_pickup_datetime`
- `geomesa.stats.enable` - Enable statistics collection for faster queries
- `geomesa.feature.expiration` - Feature expiration period (ISO 8601 duration, e.g., "P30D" for 30 days)

## Geometry Considerations

### Point Geometry
- Use when representing trips as single points (pickup or dropoff locations)
- Requires coordinate data (latitude/longitude)
- Can be derived from `PULocationID`/`DOLocationID` by joining with a Taxi Zone lookup table

### LineString Geometry
- Use when you have actual route coordinates (GPS tracking data)
- Requires a sequence of coordinates representing the trip path
- Better for visualizing actual trip routes

## Data Type Mappings

| Parquet Type | GeoMesa SFT Type | Notes |
|--------------|-------------------|-------|
| `int` | `Integer` | 32-bit integer |
| `long` | `Long` | 64-bit integer |
| `double` | `Double` | 64-bit floating point |
| `string` | `String` | Character string |
| `local-timestamp-micros` | `Date` | Timestamp (millisecond precision) |

## Notes

- The SFT uses SRID 4326 (WGS84) for geometry coordinates
- The default time field is set to `lpep_pickup_datetime` for temporal indexing
- Statistics are enabled by default for better query performance
- All fields are nullable to match the Parquet schema (nullable fields in Parquet)
- Indexed fields are selected based on common query patterns (time, location, amount filters)

## Customization

You can customize the SFT files by:
- Adding or removing indexed fields based on your query patterns
- Changing the default time field (`geomesa.index.dtg`)
- Adjusting feature expiration periods
- Adding additional user-data options for your specific GeoMesa backend

