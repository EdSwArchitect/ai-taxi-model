# AI Taxi Model

A Java 24 Maven project for reading and processing NYC Taxi and Limousine Commission (TLC) trip data from Parquet files. This project provides data models and readers for both Yellow Taxi and Green Taxi (SHL) trip records.

## Features

- **Data Models**: Java records for Yellow Taxi and Green Taxi trip data with validation
- **Parquet File Reading**: Efficient readers for processing large Parquet files
- **Schema Validation**: Automatic validation of Parquet file schemas
- **Schema Constants**: Pre-defined JSON schemas for Yellow and Green taxi data (`TaxiParquetSchemas`)
- **Schema Comparison**: Automatic schema detection by comparing Parquet file schemas to constants
- **JSON Support**: Full JSON serialization/deserialization support using Jackson
- **Type-Safe Enums**: Enum classes for VendorID, RatecodeID, and TripType
- **etcd Configuration**: Automatic configuration loading from etcd with fallback to properties files
- **Quarkus Services**: 
  - `TaxiMonitor`: Automated file monitoring and indexing to OpenSearch (polling-based)
  - `ParquetFileDirectoryMonitor`: Real-time directory monitoring using WatchService with database storage
  - `ParquetToDatabaseService`: Converts Parquet files to PostgreSQL tables
- **OpenSearch Integration**: Bulk indexing of taxi trip data
- **PostgreSQL Integration**: Automatic table creation and data insertion from Parquet files
- **Metrics & Monitoring**: Micrometer metrics with Prometheus export and Grafana dashboards
- **Docker Compose**: Complete infrastructure setup (PostgreSQL, Kafka, OpenSearch, Prometheus, Grafana)
- **Comprehensive Testing**: Full test coverage with JUnit 5 and Mockito

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
│   │   │       ├── service/           # Quarkus services
│   │   │       │   ├── TaxiMonitor.java
│   │   │       │   ├── ParquetFileDirectoryMonitor.java
│   │   │       │   ├── ParquetToDatabaseService.java
│   │   │       │   └── OpenSearchService.java
│   │   │       ├── config/            # Configuration sources
│   │   │       │   └── EtcdConfigSource.java  # etcd configuration source
│   │   │       ├── util/              # Utility classes
│   │   │       │   └── ParquetSampler.java
│   │   │       └── TaxiParquetSchemas.java  # Schema constants (YELLOW, GREEN)
│   │   └── resources/
│   │       ├── log4j2.xml              # Logging configuration
│   │       └── application.properties  # Quarkus configuration
│   └── test/
│       ├── java/                        # Test classes
│       │   └── com/bscllc/ai/text/model/
│       │       └── util/
│       │           ├── ParquetSamplerTest.java
│       │           └── ParquetSchemaPrinterTest.java  # Schema printing utility
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
- **Mockito** (5.11.0) - Mocking framework for unit tests
- **Apache Parquet** (1.14.3) - Parquet file reading
- **Apache Avro** (1.11.3) - Data serialization
- **Apache Hadoop** (3.3.6) - File system support for Parquet
- **OpenSearch REST Client** - OpenSearch Java client for indexing data
- **PostgreSQL JDBC Driver** - Database connectivity for ParquetToDatabaseService and testing
- **Micrometer** (via Quarkus) - Metrics collection and Prometheus export
- **jetcd** (0.8.0) - etcd Java client for configuration management

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
- **Utility Tests**:
  - `ParquetSamplerTest`: Tests for sampling records from Parquet files
  - `ParquetSchemaPrinterTest`: Tests for printing Parquet file schemas in JSON format
- **Service Tests**: 
  - TaxiMonitor integration tests
  - ParquetFileDirectoryMonitor integration tests
  - ParquetToDatabaseService integration tests
  - OpenSearchService integration tests

### Test Coverage

#### TaxiMonitor Tests
The `TaxiMonitorTest` class provides comprehensive coverage for the polling-based file monitoring service:

- **Valid Schema Tests**:
  - Processing valid Green taxi Parquet files
  - Processing valid Yellow taxi Parquet files
  - Processing both Green and Yellow files together
  - File reprocessing prevention (files are not processed twice)

- **Invalid Schema Tests**:
  - Rejecting Parquet files that don't match Yellow or Green taxi schemas
  - Moving invalid schema files to error directory with `schema_mismatch` suffix
  - Handling multiple invalid schema files
  - Mixing valid and invalid schema files in the same directory

- **File Movement Tests**:
  - Successfully processed files are moved to the processed directory
  - Files with errors are moved to the error directory with appropriate suffixes

- **Metrics Verification**:
  - Files processed counter
  - Records processed counters (by taxi type)
  - Error file counters
  - OpenSearch indexing verification

#### ParquetFileDirectoryMonitor Tests
The `ParquetFileDirectoryMonitorTest` class provides comprehensive coverage for the real-time WatchService-based file monitoring:

- **File Processing Tests**:
  - Processing Green taxi Parquet files with database storage
  - Processing Yellow taxi Parquet files with database storage
  - Processing multiple files (both Green and Yellow)
  - File reprocessing prevention

- **Error Handling Tests**:
  - Handling non-Parquet files (moved to error directory)
  - Handling SQLException during database processing
  - Handling IOException during file processing
  - File movement to error directory with appropriate suffixes

- **File Movement Tests**:
  - Successfully processed files moved to processed directory
  - Error files moved to error directory with error reason suffixes

- **Metrics Verification**:
  - Files processed counter (`parquet.monitor.files.processed`)
  - Records processed counters by taxi type (`parquet.monitor.green.records`, `parquet.monitor.yellow.records`)
  - Error counters (`parquet.monitor.files.errored`, `parquet.monitor.processing.errors`)

- **Service Lifecycle Tests**:
  - Monitoring service start/stop functionality

#### ParquetToDatabaseService Tests
The `ParquetToDatabaseServiceTest` class provides comprehensive coverage for Parquet to database conversion:

- **Table Creation**: Automatic table creation from Parquet schema in PostgreSQL
- **Data Insertion**: Batch insertion of records into PostgreSQL tables
- **Schema Handling**: Avro to SQL type conversion (PostgreSQL types)
- **Error Handling**: Missing files, invalid paths, database connection errors
- **PostgreSQL Compatibility**: Uses PostgreSQL-specific SQL queries and table management
- **Test Database**: Requires PostgreSQL to be running (tests skip gracefully if unavailable)

**Test Database Requirements**:
- PostgreSQL 15+ (or compatible version)
- Database `ai_taxi_model` must exist (created automatically by docker-compose)
- User `postgres` with appropriate permissions

**Test Cleanup**:
- Tests automatically clean up created tables after each test
- Test tables are identified by naming patterns and dropped safely

#### Running Tests

**Prerequisites for Database Tests**:
The `ParquetToDatabaseServiceTest` requires a PostgreSQL database. Tests will automatically skip if PostgreSQL is not available.

1. **Start PostgreSQL** (using Docker Compose):
   ```bash
   docker-compose up -d postgres
   ```

2. **Verify PostgreSQL is running**:
   ```bash
   psql -h localhost -U postgres -d ai_taxi_model -c "SELECT 1"
   ```

**Default Test Database Configuration**:
- Host: `localhost`
- Port: `5432`
- Database: `ai_taxi_model`
- Username: `postgres`
- Password: `postgres`
- Schema: `public`

**Customize Test Database Settings**:
You can override the default test database settings using system properties:
```bash
mvn test -Dtest.db.host=localhost \
         -Dtest.db.port=5432 \
         -Dtest.db.name=ai_taxi_model \
         -Dtest.db.username=postgres \
         -Dtest.db.password=postgres \
         -Dtest.db.schema=public
```

**Run all tests**:
```bash
mvn test
```

**Run specific test class**:
```bash
mvn test -Dtest=TaxiMonitorTest
```

**Run specific test method**:
```bash
mvn test -Dtest=TaxiMonitorTest#testProcessGoodGreenParquetFile
```

**Run ParquetFileDirectoryMonitor tests**:
```bash
mvn test -Dtest=ParquetFileDirectoryMonitorTest
```

**Run ParquetToDatabaseService tests**:
```bash
# Ensure PostgreSQL is running first
docker-compose up -d postgres
mvn test -Dtest=ParquetToDatabaseServiceTest
```

**Run utility tests**:
```bash
# Test Parquet schema printing
mvn test -Dtest=ParquetSchemaPrinterTest

# Test Parquet sampling
mvn test -Dtest=ParquetSamplerTest
```

**Note**: Tests that require PostgreSQL will automatically skip with a helpful message if the database is not available. This allows the test suite to run in environments without PostgreSQL, though those specific tests will be skipped.

## Data Files

Place Parquet files in one of these locations:
- Project root directory
- `data/` directory
- `src/test/resources/` directory (for tests)

Example file names:
- `yellow_tripdata_2025_01.parquet`
- `green_tripdata_2025_01.parquet`

## Schema Constants

The project includes pre-defined JSON schemas for Yellow and Green taxi data in `TaxiParquetSchemas`:

```java
import com.bscllc.ai.text.model.TaxiParquetSchemas;

// Access Yellow taxi schema
String yellowSchema = TaxiParquetSchemas.YELLOW;

// Access Green taxi schema
String greenSchema = TaxiParquetSchemas.GREEN;
```

These schemas are used by `ParquetFileDirectoryMonitor` to automatically detect and match Parquet file schemas. The schemas are stored as JSON strings and can be used for:
- Schema validation
- Schema comparison
- Documentation
- Testing

### Printing Parquet Schemas

You can use the `ParquetSchemaPrinterTest` to print the schema of any Parquet file:

```bash
mvn test -Dtest=ParquetSchemaPrinterTest#testPrintYellowTripdataSchema
mvn test -Dtest=ParquetSchemaPrinterTest#testPrintGreenTripdataSchema
```

This will output the schema in JSON format, which can be useful for:
- Verifying schema compatibility
- Understanding file structure
- Debugging schema mismatches

## Configuration Management

The application supports configuration from multiple sources with automatic fallback:

1. **etcd** (Priority: 250) - Distributed key-value store for centralized configuration
2. **Properties Files** (Priority: 100) - Local `application.properties` files

### etcd Configuration

The application automatically attempts to load configuration from etcd on startup. If etcd is available, configuration values from etcd take precedence over properties files. If etcd is unavailable, the application gracefully falls back to properties files.

**Configuration Keys in etcd:**
- All configuration keys are stored under the prefix: `/ai-taxi-model/config/`
- Example: `/ai-taxi-model/config/taxi.monitor.enabled`

**etcd Connection:**
- Default endpoint: `http://localhost:2379`
- Configurable via environment variables:
  - `ETCD_HOST` (default: `localhost`)
  - `ETCD_PORT` (default: `2379`)
- Or via system properties: `-DETCD_HOST=etcd -DETCD_PORT=2379`

**Populating etcd Configuration:**
```bash
# Start etcd
docker-compose up -d etcd

# Populate configuration
./scripts/populate-etcd-config.sh [profile]

# View configuration
./scripts/list-etcd-config.sh
```

**Configuration Caching:**
- etcd configuration is cached for 30 seconds to reduce load
- Cache is automatically refreshed when stale

See [README-DOCKER.md](README-DOCKER.md) for detailed etcd setup and usage instructions.

## Quarkus Services

The project includes multiple services for processing taxi data:

### TaxiMonitor Service
`TaxiMonitor` and `OpenSearchService` work together to automatically monitor a directory for Parquet files and index them to OpenSearch using polling-based monitoring (checks every 30 seconds).

### ParquetFileDirectoryMonitor Service
`ParquetFileDirectoryMonitor` uses Java's `WatchService` API for real-time file monitoring and processes files using `ParquetToDatabaseService` to store data in PostgreSQL. This service automatically starts when the Quarkus application starts and monitors the input directory for new Parquet files.

**Key Features**:
- Real-time file detection (no polling delay)
- Configurable batch processing with timer-based flushing
- Automatic schema detection (Green vs Yellow taxi)
- Database table creation and data insertion with explicit commits
- Async file processing to avoid blocking
- Comprehensive error handling and file management
- Metrics tracking for monitoring

## TaxiMonitor Service (OpenSearch)

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

2. **Create input, error, and processed directories**:
   ```bash
   mkdir -p ./data/input
   mkdir -p ./data/error
   mkdir -p ./data/processed
   ```

### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Directory to monitor for Parquet files
taxi.monitor.input.dir=./data/input

# Directory for files that fail processing
taxi.monitor.error.dir=./data/error

# Directory for successfully processed files
taxi.monitor.processed.dir=./data/processed

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
   - Moves successfully processed files to the processed directory
   - Moves non-Parquet files or schema-mismatched files to the error directory

2. **OpenSearchService**:
   - Handles connections to OpenSearch with authentication and optional TLS
   - Performs bulk indexing operations for efficient data loading
   - Tracks indexing metrics (documents indexed, errors, bulk operations)

3. **Processing Flow**:
   - Files are processed in batches of 1000 records
   - Records are indexed to `yellowtaxi` or `greentaxi` indices based on schema
   - Successfully processed files are moved to the processed directory (preserving original filename)
   - Files with errors are moved to the error directory with a reason suffix (e.g., `filename_schema_mismatch.parquet`)
   - Processed files are tracked to avoid reprocessing

4. **File Lifecycle**:
   - **Input Directory** (`taxi.monitor.input.dir`): New Parquet files are placed here for processing
   - **Processed Directory** (`taxi.monitor.processed.dir`): Successfully processed files are moved here
   - **Error Directory** (`taxi.monitor.error.dir`): Files that fail processing are moved here with error reason suffixes:
     - `_not_parquet` - File is not a Parquet file
     - `_schema_mismatch` - File doesn't match Yellow or Green taxi schema
     - `_processing_error` - Error occurred during processing/indexing

### Running the Service

#### Development Mode (Recommended for Testing)

```bash
# Start the service with hot reload (uses dev profile automatically)
mvn quarkus:dev

# Or explicitly specify dev profile
mvn quarkus:dev -Dquarkus.profile=dev
```

The service will:
- Start on port 8080
- Monitor `./data/input` directory every 30 seconds
- Expose metrics at `http://localhost:8080/q/metrics`
- Automatically reload on code changes
- Use DEBUG logging level for detailed diagnostics

**Development Profile Features**:
- Verbose logging (DEBUG level)
- Localhost OpenSearch connection
- Local file paths for input/error/processed directories

#### Production Mode

```bash
# Build the application
mvn clean package

# Run with production profile
java -jar target/quarkus-app/quarkus-run.jar -Dquarkus.profile=prod

# Or set via environment variable
export QUARKUS_PROFILE=prod
java -jar target/quarkus-app/quarkus-run.jar
```

**Production Profile Features**:
- INFO level logging (less verbose)
- Environment variable support for configuration
- Production-optimized file paths (`/var/lib/taxi-monitor/input`, `/var/lib/taxi-monitor/error`, `/var/lib/taxi-monitor/processed`)
- Configurable via environment variables:
  - `OPENSEARCH_HOST` - OpenSearch hostname
  - `OPENSEARCH_PORT` - OpenSearch port
  - `OPENSEARCH_SCHEME` - http or https
  - `OPENSEARCH_USERNAME` - OpenSearch username
  - `OPENSEARCH_PASSWORD` - OpenSearch password
  - `QUARKUS_HTTP_PORT` - HTTP server port

#### Profile Configuration

Quarkus profiles are configured in:
- `application.properties` - Default configuration
- `application-dev.properties` - Development profile
- `application-prod.properties` - Production profile

The active profile can be set via:
- Command line: `-Dquarkus.profile=dev` or `-Dquarkus.profile=prod`
- Environment variable: `QUARKUS_PROFILE=dev` or `QUARKUS_PROFILE=prod`
- In `application.properties`: `quarkus.profile=dev` (not recommended for production)

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
   # "Successfully processed file: yellow_tripdata_2025_01.parquet"
   # "Moved successfully processed file yellow_tripdata_2025_01.parquet to processed directory"
   ```

3. **Verify file movement**:
   ```bash
   # Check that the file was moved to processed directory
   ls -lh ./data/processed/
   
   # Check error directory if processing failed
   ls -lh ./data/error/
   ```

4. **Verify data in OpenSearch**:
   ```bash
   # Check if indices were created
   curl -u admin:admin -k https://localhost:9200/_cat/indices?v
   
   # Query yellow taxi data
   curl -u admin:admin -k https://localhost:9200/yellowtaxi/_search?size=5
   
   # Query green taxi data
   curl -u admin:admin -k https://localhost:9200/greentaxi/_search?size=5
   ```

5. **Check metrics**:
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

### ParquetFileDirectoryMonitor Service (PostgreSQL)

#### Overview
The `ParquetFileDirectoryMonitor` service provides real-time directory monitoring for Parquet files and automatically processes them into PostgreSQL tables using `ParquetToDatabaseService`.

#### Prerequisites

1. **PostgreSQL must be running**:
   ```bash
   # Start PostgreSQL using Docker Compose
   docker-compose up -d postgres
   
   # Verify PostgreSQL is accessible
   psql -h localhost -U postgres -d ai_taxi_model
   ```

2. **Create input, error, and processed directories**:
   ```bash
   mkdir -p ./data/input
   mkdir -p ./data/error
   mkdir -p ./data/processed
   ```

#### Configuration

Edit `src/main/resources/application.properties`:

```properties
# Directory to monitor for Parquet files (used by both TaxiMonitor and ParquetFileDirectoryMonitor)
taxi.monitor.input.dir=./data/input

# Directory for files that fail processing
taxi.monitor.error.dir=./data/error

# Directory for successfully processed files
taxi.monitor.processed.dir=./data/processed

# ParquetFileDirectoryMonitor batch processing configuration
parquet.monitor.batch.size=10                    # Number of files to process in a batch
parquet.monitor.batch.timer.seconds=30           # Timer interval (seconds) to process batches if size isn't reached

# Database configuration (for ParquetToDatabaseService)
db.url=jdbc:postgresql://localhost:5432/ai_taxi_model
db.username=postgres
db.password=postgres
db.schema=public

# Database batch processing configuration
parquet.database.batch.size=1000                 # Number of records per database batch insert
```

#### How It Works

1. **Real-Time Monitoring**:
   - Uses Java's `WatchService` API to detect file system events
   - Monitors for `ENTRY_CREATE` and `ENTRY_MODIFY` events
   - Automatically starts monitoring when the service is initialized
   - Detected files are queued for batch processing

2. **Batch Processing**:
   - Files are collected in a queue and processed in batches
   - Batch processing is triggered by two conditions:
     - **Batch Size**: When the queue reaches `parquet.monitor.batch.size` files, batch processing starts immediately
     - **Timer**: A scheduled task runs every 10 seconds and checks if `parquet.monitor.batch.timer.seconds` has elapsed since the last batch. If so, any queued files are processed
   - This ensures data appears in the database periodically, not just when the application exits

3. **Schema Detection**:
   - Extracts the Parquet file schema as JSON
   - Compares the schema against constants in `TaxiParquetSchemas` (YELLOW and GREEN)
   - Uses JSON comparison to match schemas (handles field ordering differences)
   - Invalid schema files are moved to the error directory with `schema_mismatch` suffix

4. **Database Processing**:
   - Creates PostgreSQL tables dynamically based on Parquet file schema
   - **Table naming**: Uses schema name directly - `GREEN` or `YELLOW` (all files with the same schema type are inserted into the same table)
   - Inserts records in batches (configurable via `parquet.database.batch.size`, default: 1000)
   - **Explicit Commits**: Each database batch is explicitly committed, ensuring data is immediately visible in the database
   - Final commit ensures all data is persisted before marking the file as processed

5. **Async Processing**:
   - Files are processed asynchronously using a dedicated thread pool executor to avoid blocking the monitoring loop
   - The monitoring loop runs in a separate single-threaded executor, ensuring file detection is never blocked
   - File processing uses a thread pool executor (5 threads by default), allowing multiple files to be processed concurrently
   - Prevents duplicate processing by tracking processed files

6. **File Lifecycle**:
   - **Input Directory**: New Parquet files are detected in real-time and queued
   - **Processed Directory**: Successfully processed files are moved here after database commit
   - **Error Directory**: Files with errors are moved here with error reason suffixes:
     - `_not_parquet` - File is not a Parquet file
     - `_schema_mismatch` - File doesn't match Green or Yellow taxi schema
     - `_processing_error` - Error occurred during database processing
     - `_unexpected_error` - Unexpected error occurred

#### Running the Service

The service automatically starts when the Quarkus application starts:

```bash
mvn quarkus:dev
```

The service will:
- Start monitoring `./data/input` directory immediately
- Queue any existing `.parquet` files in the input directory for batch processing
- Process batches when batch size is reached or timer elapses
- Continue monitoring for new files in real-time

#### Testing the Service

1. **Place a Parquet file in the input directory**:
   ```bash
   cp src/test/resources/yellow_tripdata_2025_01.parquet ./data/input/
   # Or
   cp src/test/resources/green_tripdata_2025_01.parquet ./data/input/
   ```

2. **Monitor the logs**:
   ```bash
   # Watch for processing messages
   # You should see logs like:
   # "Detected new parquet file: yellow_tripdata_2025_01.parquet"
   # "File yellow_tripdata_2025_01.parquet matches YELLOW schema"
   # "Processing file: yellow_tripdata_2025_01.parquet"
   # "Processing Parquet file: ..."
   # "Successfully processed X records from ... into table YELLOW"
   # "Moved successfully processed file ... to processed directory"
   ```

3. **Verify database tables**:
   ```bash
   # Connect to PostgreSQL
   psql -h localhost -U postgres -d ai_taxi_model
   
   # List tables
   \dt
   
   # Query data from tables (all files with same schema use the same table)
   SELECT COUNT(*) FROM "YELLOW";
   SELECT * FROM "YELLOW" LIMIT 5;
   SELECT COUNT(*) FROM "GREEN";
   SELECT * FROM "GREEN" LIMIT 5;
   ```

4. **Verify file movement**:
   ```bash
   # Check that the file was moved to processed directory
   ls -lh ./data/processed/
   
   # Check error directory if processing failed
   ls -lh ./data/error/
   ```

5. **Check metrics**:
   ```bash
   # View Prometheus metrics
   curl http://localhost:8080/q/metrics | grep parquet.monitor
   ```

#### Service Components

##### ParquetFileDirectoryMonitor
- **Purpose**: Real-time directory monitoring using WatchService
- **Initialization**: Automatically starts when Quarkus application starts
- **Architecture**: Uses separate executor services for monitoring (single-threaded) and file processing (thread pool with 5 threads) to ensure monitoring is never blocked
- **Processing**: Async file processing with batch queuing and duplicate prevention
- **Batch Processing**: 
  - Files are queued and processed when batch size is reached or timer elapses
  - Configurable batch size (`parquet.monitor.batch.size`) and timer interval (`parquet.monitor.batch.timer.seconds`)
  - Files are processed concurrently in separate threads, allowing multiple files to be processed simultaneously
- **Metrics**: Tracks files processed, records processed, and errors
- **Location**: `com.bscllc.ai.text.model.service.ParquetFileDirectoryMonitor`

##### ParquetToDatabaseService
- **Purpose**: Converts Parquet files to PostgreSQL tables
- **Features**: 
  - Automatic schema detection and table creation
  - Avro to SQL type mapping
  - Batch insertion for performance (configurable batch size via `parquet.database.batch.size`)
  - Explicit transaction commits after each batch for immediate data visibility
  - Table name sanitization
- **Location**: `com.bscllc.ai.text.model.service.ParquetToDatabaseService`

##### TaxiParquetSchemas
- **Purpose**: Contains JSON schema constants for Yellow and Green taxi Parquet files
- **Constants**: 
  - `YELLOW`: JSON schema for Yellow taxi trip data
  - `GREEN`: JSON schema for Green taxi trip data
- **Usage**: Used by `ParquetFileDirectoryMonitor` for schema comparison
- **Location**: `com.bscllc.ai.text.model.TaxiParquetSchemas`

#### Troubleshooting

**Service not detecting files**:
- Ensure the input directory exists and is accessible
- Verify files have `.parquet` extension
- Check service logs for WatchService errors
- Ensure files are not locked by other processes

**Database connection errors**:
- Verify PostgreSQL is running: `psql -h localhost -U postgres -d ai_taxi_model`
- Check database credentials in `application.properties`
- Ensure the database and schema exist

**Table creation errors**:
- Check PostgreSQL logs for SQL errors
- Verify user has CREATE TABLE permissions
- Note: All files with the same schema type (YELLOW or GREEN) are inserted into the same table

**Files not being processed**:
- Check that files match Yellow or Green taxi schemas
- Review error directory for files with error suffixes
- Check service logs for processing errors
- Verify file processing executor service is running (check logs for "ParquetFileDirectoryMonitor-FileProcessor" thread activity)
- **Data not appearing in database**: 
  - Ensure batch timer has elapsed (default: 30 seconds) or batch size is reached (default: 10 files)
  - Check that database batches are being committed (look for commit logs)
  - Verify `parquet.database.batch.size` is not too large if you need more frequent commits
  - Check database transaction logs for any rollbacks
  - Ensure file processing threads are not blocked (monitoring loop uses a separate executor to prevent blocking)

### Troubleshooting

**Service won't start**:
- Verify OpenSearch is running and accessible
- Check OpenSearch credentials in `application.properties`
- Ensure input/error/processed directories exist

**Files not being processed**:
- Check that files are `.parquet` format
- Verify files match Yellow or Green taxi schemas
- Check processed directory for successfully processed files
- Check error directory for files that failed processing
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
- `taxi.monitor.files.processed` - Total files processed
- `taxi.monitor.yellow.files` - Yellow taxi files processed
- `taxi.monitor.yellow.records` - Yellow taxi records processed
- `taxi.monitor.green.files` - Green taxi files processed
- `taxi.monitor.green.records` - Green taxi records processed
- `taxi.monitor.files.errored` - Files that failed processing

#### ParquetFileDirectoryMonitor Metrics
- `parquet.monitor.files.processed` - Total parquet files processed by directory monitor
- `parquet.monitor.yellow.files` - Yellow taxi files processed
- `parquet.monitor.yellow.records` - Yellow taxi records processed
- `parquet.monitor.green.files` - Green taxi files processed
- `parquet.monitor.green.records` - Green taxi records processed
- `parquet.monitor.files.errored` - Files that failed processing
- `parquet.monitor.processing.errors` - Processing errors encountered

#### OpenSearchService Metrics
- `opensearch_documents_indexed_total` - Total documents indexed
- `opensearch_yellow_documents_total` - Yellow taxi documents indexed
- `opensearch_green_documents_total` - Green taxi documents indexed
- `opensearch_bulk_operations_total` - Bulk index operations
- `opensearch_yellow_bulk_operations_total` - Yellow taxi bulk operations
- `opensearch_green_bulk_operations_total` - Green taxi bulk operations
- `opensearch_indexing_errors_total` - Indexing errors

#### ParquetToDatabaseService Metrics
- `parquet.database.files.processed` - Total number of Parquet files processed to database
- `parquet.database.records.inserted` - Total number of records inserted into database
- `parquet.database.tables.created` - Total number of database tables created
- `parquet.database.batch.operations` - Total number of batch insert operations performed
- `parquet.database.batch.insert.success` - Total number of successful batch inserts
- `parquet.database.batch.insert.failure` - Total number of failed batch inserts
- `parquet.database.errors` - Total number of database errors encountered
- `parquet.database.processing.time` - Timer metric for time taken to process Parquet files (includes duration, count, max, etc.)

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
**Dashboard**: "Taxi Monitor, OpenSearch & Database Metrics"

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

