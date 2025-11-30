# AI Taxi Model

A Java 24 Maven project for reading and processing NYC Taxi and Limousine Commission (TLC) trip data from Parquet files. This project provides data models and readers for both Yellow Taxi and Green Taxi (SHL) trip records.

## Features

- **Data Models**: Java records for Yellow Taxi and Green Taxi trip data with validation
- **Parquet File Reading**: Efficient readers for processing large Parquet files
- **Schema Validation**: Automatic validation of Parquet file schemas
- **JSON Support**: Full JSON serialization/deserialization support using Jackson
- **Type-Safe Enums**: Enum classes for VendorID, RatecodeID, and TripType
- **Quarkus Service**: Automated file monitoring and indexing to OpenSearch
- **OpenSearch Integration**: Bulk indexing of taxi trip data
- **Metrics & Monitoring**: Micrometer metrics with Prometheus export and Grafana dashboards
- **Docker Compose**: Complete infrastructure setup (PostgreSQL, Kafka, OpenSearch, Prometheus, Grafana)
- **Comprehensive Testing**: Full test coverage with JUnit 5

## Requirements

- Java 24
- Maven 3.6+

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
│   │   │       ├── input/              # Parquet file readers
│   │   │       │   ├── YellowReader.java
│   │   │       │   └── GreenReader.java
│   │   │       └── service/            # Quarkus services
│   │   │           ├── TaxiMonitor.java
│   │   │           └── OpenSearchService.java
│   │   └── resources/
│   │       ├── log4j2.xml              # Logging configuration
│   │       └── application.properties  # Quarkus configuration
│   └── test/
│       ├── java/                        # Test classes
│       └── resources/
│           ├── log4j2.xml              # Test logging configuration
│           ├── yellow_tripdata_2025_01.parquet
│           └── green_tripdata_2025_01.parquet
└── pom.xml                              # Maven configuration
```

## Dependencies

- **Quarkus** (3.27.1) - Java framework for building cloud-native applications
- **Log4j2** (2.23.1) - Logging framework
- **SLF4J** (2.0.16) - Logging facade with Log4j2 bridge
- **Jackson** (2.18.1) - JSON processing
- **JUnit 5** (5.10.2) - Testing framework
- **Apache Parquet** (1.14.3) - Parquet file reading
- **Apache Avro** (1.11.3) - Data serialization
- **Apache Hadoop** (3.3.6) - File system support for Parquet
- **OpenSearch REST Client** - OpenSearch Java client for indexing data
- **Micrometer** (via Quarkus) - Metrics collection and Prometheus export

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

#### Run Quarkus Service

```bash
mvn quarkus:dev
```

Or run the packaged JAR:

```bash
java -jar target/ai-text-model-1.0-SNAPSHOT.jar
```

## Docker Setup

The project includes a Docker Compose configuration for running supporting services. See [README-DOCKER.md](README-DOCKER.md) for detailed instructions.

Quick start:

```bash
# Start all services (PostgreSQL, Kafka, OpenSearch, Prometheus, Grafana)
docker-compose up -d

# Or with TLS enabled
./scripts/generate-certs.sh
docker-compose -f docker-compose.yml -f docker-compose.tls.yml up -d
```

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

## Quarkus Service

The `TaxiMonitor` and `OpenSearchService` work together to automatically monitor a directory for Parquet files and index them to OpenSearch.

### Prerequisites

1. **OpenSearch must be running**:
   ```bash
   # Start OpenSearch using Docker Compose
   docker-compose up -d opensearch
   
   # Verify OpenSearch is accessible
   curl -u admin:admin -k https://localhost:9200/_cluster/health
   # Or if using HTTP (no TLS):
   curl http://localhost:9200/_cluster/health
   ```

2. **Create input and error directories**:
   ```bash
   mkdir -p ./data/input
   mkdir -p ./data/error
   ```

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Directory to monitor for Parquet files
taxi.monitor.input.dir=./data/input

# Directory for files that fail processing
taxi.monitor.error.dir=./data/error

# OpenSearch connection
opensearch.host=localhost
opensearch.port=9200
opensearch.scheme=https          # Use 'https' if OpenSearch has TLS enabled, 'http' otherwise
opensearch.username=admin
opensearch.password=admin        # Change to match your OpenSearch password
```

**Note**: If OpenSearch is running with TLS/SSL enabled (default in docker-compose.yml), set `opensearch.scheme=https`. For HTTP-only setups, use `opensearch.scheme=http`.

### How It Works

1. **TaxiMonitor Service**:
   - Monitors the input directory every 30 seconds for new Parquet files
   - Automatically detects whether files match Yellow Taxi or Green Taxi schemas
   - Converts Parquet records to `YellowTaxi` or `GreenTaxi` objects
   - Moves non-Parquet files or schema-mismatched files to the error directory

2. **OpenSearchService**:
   - Handles connections to OpenSearch with authentication and optional TLS
   - Performs bulk indexing operations for efficient data loading
   - Tracks indexing metrics (documents indexed, errors, bulk operations)

3. **Processing Flow**:
   - Files are processed in batches of 1000 records
   - Records are indexed to `yellowtaxi` or `greentaxi` indices based on schema
   - Processed files are tracked to avoid reprocessing

### Running the Service

#### Development Mode (Recommended for Testing)

```bash
# Start the service with hot reload
mvn quarkus:dev
```

The service will:
- Start on port 8080
- Monitor `./data/input` directory every 30 seconds
- Expose metrics at `http://localhost:8080/q/metrics`
- Automatically reload on code changes

#### Production Mode

```bash
# Build the application
mvn clean package

# Run the packaged JAR
java -jar target/quarkus-app/quarkus-run.jar
```

### Testing the Service

1. **Place a Parquet file in the input directory**:
   ```bash
   # Copy a test file to the input directory
   cp src/test/resources/yellow_tripdata_2025_01.parquet ./data/input/
   # Or
   cp src/test/resources/green_tripdata_2025_01.parquet ./data/input/
   ```

2. **Monitor the logs**:
   ```bash
   # Watch for processing messages
   # You should see logs like:
   # "Processing file: yellow_tripdata_2025_01.parquet"
   # "Processing Yellow taxi file: yellow_tripdata_2025_01.parquet"
   # "Indexed X Yellow taxi records from file: yellow_tripdata_2025_01.parquet"
   ```

3. **Verify data in OpenSearch**:
   ```bash
   # Check if indices were created
   curl -u admin:admin -k https://localhost:9200/_cat/indices?v
   
   # Query yellow taxi data
   curl -u admin:admin -k https://localhost:9200/yellowtaxi/_search?size=5
   
   # Query green taxi data
   curl -u admin:admin -k https://localhost:9200/greentaxi/_search?size=5
   ```

4. **Check metrics**:
   ```bash
   # View Prometheus metrics
   curl http://localhost:8080/q/metrics | grep taxi_monitor
   curl http://localhost:8080/q/metrics | grep opensearch
   ```

### Service Components

#### TaxiMonitor
- **Purpose**: Monitors directory and processes Parquet files
- **Schedule**: Runs every 30 seconds (`@Scheduled(every = "30s")`)
- **Metrics**: Tracks files processed, records processed, and errors
- **Location**: `com.bscllc.ai.text.model.service.TaxiMonitor`

#### OpenSearchService
- **Purpose**: Manages OpenSearch connections and indexing
- **Features**: Bulk indexing, error handling, metrics tracking
- **Location**: `com.bscllc.ai.text.model.service.OpenSearchService`

### Troubleshooting

**Service won't start**:
- Verify OpenSearch is running and accessible
- Check OpenSearch credentials in `application.properties`
- Ensure input/error directories exist

**Files not being processed**:
- Check that files are `.parquet` format
- Verify files match Yellow or Green taxi schemas
- Check error directory for moved files
- Review service logs for error messages

**Connection errors to OpenSearch**:
- Verify OpenSearch is running: `curl -u admin:admin -k https://localhost:9200/_cluster/health`
- Check `opensearch.scheme` matches your OpenSearch configuration (http vs https)
- Verify username and password are correct
- If using HTTPS, ensure certificates are properly configured

## Logging

The project uses Log4j2 for logging with SLF4J bridge support.

**Main Configuration**: `src/main/resources/log4j2.xml`
- Application loggers: INFO level
- Third-party loggers: WARN level

**Test Configuration**: `src/test/resources/log4j2-test.xml`
- Application loggers: DEBUG level (more verbose for tests)
- Third-party loggers: WARN level

**Quarkus Logging**: Configured in `application.properties`
- Default: INFO level
- Application packages: DEBUG level

## Metrics & Monitoring

The project includes comprehensive metrics collection using Micrometer and Prometheus.

### Metrics Endpoint

The Quarkus service exposes Prometheus metrics at:
- **URL**: `http://localhost:8080/q/metrics`
- **Format**: Prometheus text format

### Available Metrics

#### TaxiMonitor Metrics
- `taxi_monitor_files_processed_total` - Total files processed
- `taxi_monitor_yellow_files_total` - Yellow taxi files processed
- `taxi_monitor_yellow_records_total` - Yellow taxi records processed
- `taxi_monitor_green_files_total` - Green taxi files processed
- `taxi_monitor_green_records_total` - Green taxi records processed
- `taxi_monitor_files_errored_total` - Files that failed processing

#### OpenSearchService Metrics
- `opensearch_documents_indexed_total` - Total documents indexed
- `opensearch_yellow_documents_total` - Yellow taxi documents indexed
- `opensearch_green_documents_total` - Green taxi documents indexed
- `opensearch_bulk_operations_total` - Bulk index operations
- `opensearch_yellow_bulk_operations_total` - Yellow taxi bulk operations
- `opensearch_green_bulk_operations_total` - Green taxi bulk operations
- `opensearch_indexing_errors_total` - Indexing errors

### Prometheus Integration

Prometheus is configured to scrape metrics from:
- **Taxi Monitor Service**: `host.docker.internal:8080/q/metrics`
- **OpenSearch**: `opensearch:9200/_prometheus/metrics` (requires Prometheus exporter plugin)

See `prometheus/prometheus.yml` for full configuration.

### Grafana Dashboard

A pre-configured Grafana dashboard is available with:
- File processing metrics (rate and totals)
- Record processing metrics by taxi type
- OpenSearch indexing metrics
- Error tracking
- Real-time monitoring with 10-second refresh

**Access**: http://localhost:3000 (admin/admin)
**Dashboard**: "Taxi Monitor & OpenSearch Metrics"

See [README-DOCKER.md](README-DOCKER.md) for detailed setup instructions.

## License

This project is part of the AI Playground workspace.

## Infrastructure

The project includes Docker Compose configuration for:

- **PostgreSQL 15**: Relational database
- **Kafka (KRaft)**: Event streaming platform
- **OpenSearch 3.3.2**: Search and analytics engine
- **OpenSearch Dashboards 3.3.0**: Visualization UI
- **Prometheus**: Metrics collection
- **Grafana 12.3.0**: Metrics visualization

See [README-DOCKER.md](README-DOCKER.md) for detailed setup instructions, including TLS configuration with self-signed certificates.

## References

- [NYC TLC Trip Record Data](http://www.nyc.gov/html/tlc/html/about/trip_record_data.shtml)
- [Yellow Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_yellow.pdf)
- [Green Taxi Data Dictionary](https://www.nyc.gov/assets/tlc/downloads/pdf/data_dictionary_trip_records_green.pdf)
- [Quarkus Documentation](https://quarkus.io/)
- [OpenSearch Documentation](https://opensearch.org/docs/)
- [Micrometer Documentation](https://micrometer.io/)
- [Prometheus Documentation](https://prometheus.io/docs/)
- [Grafana Documentation](https://grafana.com/docs/)

