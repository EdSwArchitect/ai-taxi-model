# AI Taxi Model

A Java 24 Maven project for reading and processing NYC Taxi and Limousine Commission (TLC) trip data from Parquet files. This project provides data models and readers for both Yellow Taxi and Green Taxi (SHL) trip records.

## Features

- **Data Models**: Java records for Yellow Taxi and Green Taxi trip data with validation
- **Parquet File Reading**: Efficient readers for processing large Parquet files
- **Schema Validation**: Automatic validation of Parquet file schemas
- **JSON Support**: Full JSON serialization/deserialization support using Jackson
- **Type-Safe Enums**: Enum classes for VendorID, RatecodeID, and TripType
- **Comprehensive Testing**: Full test coverage with JUnit 5

## Requirements

- Java 24
- Maven 3.6+ (for Maven builds)
- Gradle 8.11+ (for Gradle builds, optional)

## Project Structure

```
ai-taxi-model/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/bscllc/ai/text/model/
│   │   │       ├── datamodel/          # Data model classes
│   │   │       │   ├── YellowTaxi.java
│   │   │       │   ├── GreenTaxi.java
│   │   │       │   ├── VendorID.java
│   │   │       │   ├── RatecodeID.java
│   │   │       │   └── TripType.java
│   │   │       └── input/              # Parquet file readers
│   │   │           ├── YellowReader.java
│   │   │           └── GreenReader.java
│   │   └── resources/
│   │       └── log4j2.xml              # Logging configuration
│   └── test/
│       ├── java/                        # Test classes
│       └── resources/
│           ├── log4j2.xml              # Test logging configuration
│           ├── yellow_tripdata_2025_01.parquet
│           └── green_tripdata_2025_01.parquet
├── pom.xml                              # Maven configuration
├── build.gradle                         # Gradle configuration
├── settings.gradle                      # Gradle settings
├── gradle.properties                   # Gradle properties
└── gradlew                              # Gradle wrapper (Unix)
```

## Dependencies

- **Log4j2** (2.23.1) - Logging framework
- **SLF4J** (2.0.16) - Logging facade with Log4j2 bridge
- **Jackson** (2.18.1) - JSON processing
- **JUnit 5** (5.10.2) - Testing framework
- **Apache Parquet** (1.14.3) - Parquet file reading
- **Apache Avro** (1.11.3) - Data serialization
- **Apache Hadoop** (3.3.6) - File system support for Parquet

## Building the Project

### Using Maven

#### Compile

```bash
mvn clean compile
```

#### Run Tests

```bash
mvn test
```

#### Run Specific Test

```bash
mvn test -Dtest=YellowReaderTest#testReadYellowTripData2025_01
```

#### Package

```bash
mvn clean package
```

### Using Gradle

#### Compile

```bash
./gradlew clean build -x test
```

#### Run Tests

```bash
./gradlew test
```

#### Run Specific Test

```bash
./gradlew test --tests "YellowReaderTest.testReadYellowTripData2025_01"
```

#### Package

```bash
./gradlew clean build
```

**Note**: Gradle requires Java 24 to be available in your system. The Gradle daemon itself can run on Java 21+, but the project will compile using Java 24 toolchain.

## Usage Examples

### Reading Yellow Taxi Data

```java
import com.bscllc.ai.text.model.input.YellowReader;
import com.bscllc.ai.text.model.datamodel.YellowTaxi;
import java.nio.file.Paths;
import java.util.List;

// Create reader
YellowReader reader = new YellowReader();

// Read all records
List<YellowTaxi> taxis = reader.readAll(
    Paths.get("yellow_tripdata_2025_01.parquet")
);

// Process records
taxis.stream()
    .filter(YellowTaxi::isValid)
    .forEach(taxi -> {
        System.out.println("Trip duration: " + 
            taxi.getTripDurationMinutes() + " minutes");
    });
```

### Reading Green Taxi Data

```java
import com.bscllc.ai.text.model.input.GreenReader;
import com.bscllc.ai.text.model.datamodel.GreenTaxi;
import java.nio.file.Paths;
import java.util.stream.Stream;

// Create reader
GreenReader reader = new GreenReader();

// Read as stream (for large files)
try (Stream<GreenTaxi> stream = reader.read(
        Paths.get("green_tripdata_2025_01.parquet"))) {
    stream
        .filter(GreenTaxi::isValid)
        .filter(taxi -> taxi.tripType() == 1) // Street-hail only
        .forEach(System.out::println);
}
```

### Using Enums

```java
import com.bscllc.ai.text.model.datamodel.VendorID;
import com.bscllc.ai.text.model.datamodel.RatecodeID;
import com.bscllc.ai.text.model.datamodel.TripType;

// Convert code to enum
VendorID vendor = VendorID.fromCode(1);
System.out.println(vendor.getDescription()); 
// Output: "Creative Mobile Technologies, LLC"

// Validate code
boolean isValid = RatecodeID.isValid(2); // true

// Use TripType enum
TripType tripType = TripType.fromCode(1); // STREET_HAIL
```

### JSON Serialization

```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.DeserializationFeature;

// Configure ObjectMapper
ObjectMapper mapper = new ObjectMapper();
mapper.registerModule(new JavaTimeModule());
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

// Serialize to JSON
String json = mapper.writeValueAsString(yellowTaxi);

// Deserialize from JSON
YellowTaxi taxi = mapper.readValue(json, YellowTaxi.class);
```

## Data Models

### YellowTaxi

Represents a Yellow Taxi trip record with 20 fields:
- VendorID, pickup/dropoff datetimes, passenger count, trip distance
- Rate code, store and forward flag, location IDs
- Payment type, fare amounts, taxes, tips, tolls
- Congestion surcharge, airport fee, CBD congestion fee

**Data Dictionary**: [NYC TLC Yellow Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_yellow.pdf)

### GreenTaxi

Represents a Green Taxi (SHL) trip record with 20 fields:
- Similar to YellowTaxi but uses `lpep_*` datetime fields
- Includes `trip_type` field (Street-hail or Dispatch)
- No `airport_fee` field

**Data Dictionary**: [NYC TLC Green Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_green.pdf)

## Enums

### VendorID

Represents TPEP/LPEP provider codes:
- `CREATIVE_MOBILE_TECHNOLOGIES` (1)
- `CURB_MOBILITY` (2)
- `MYLE_TECHNOLOGIES` (6)
- `HELIX` (7) - Yellow taxi only

### RatecodeID

Represents rate codes:
- `STANDARD_RATE` (1)
- `JFK` (2)
- `NEWARK` (3)
- `NASSAU_OR_WESTCHESTER` (4)
- `NEGOTIATED_FARE` (5)
- `GROUP_RIDE` (6)
- `NULL_OR_UNKNOWN` (99)

### TripType

Represents trip types (Green taxi only):
- `STREET_HAIL` (1)
- `DISPATCH` (2)

## Testing

The project includes comprehensive test coverage:

- **Data Model Tests**: Validation, conversion, and edge cases
- **Reader Tests**: File reading, schema validation, error handling
- **Enum Tests**: Code conversion, validation, edge cases
- **JSON Tests**: Serialization, deserialization, round-trip testing

Run all tests:
```bash
mvn test
```

## Data Files

Place Parquet files in one of these locations:
- Project root directory
- `data/` directory
- `src/test/resources/` directory (for tests)

Example file names:
- `yellow_tripdata_2025_01.parquet`
- `green_tripdata_2025_01.parquet`

## Logging

The project uses Log4j2 for logging with SLF4J bridge support.

**Main Configuration**: `src/main/resources/log4j2.xml`
- Application loggers: INFO level
- Third-party loggers: WARN level

**Test Configuration**: `src/test/resources/log4j2.xml`
- Application loggers: DEBUG level (more verbose for tests)
- Third-party loggers: WARN level

## License

This project is part of the AI Playground workspace.

## References

- [NYC TLC Trip Record Data](http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml)
- [Yellow Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_yellow.pdf)
- [Green Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_green.pdf)

