# Docker Compose Setup

This directory contains a Docker Compose configuration for running the AI Taxi Model project with supporting services.

## Services

- **PostgreSQL 15**: Database server on port 5432
- **Kafka (KRaft)**: Apache Kafka with KRaft (no Zookeeper) on ports 9092 (PLAINTEXT) and 9094 (SSL)
- **OpenSearch 3.3.2**: Search and analytics engine on port 9200
- **OpenSearch Dashboards 3.3.2**: Visualization UI on port 5601
- **Prometheus**: Metrics collection on port 9090
- **Grafana 12.3.0**: Metrics visualization on port 3000
- **etcd 3.6**: Distributed key-value store for configuration management on ports 2379 (Client API) and 2380 (Peer)

## Quick Start

### 1. Start Services (without TLS)

```bash
docker-compose up -d
```

### 2. Start Services (with TLS)

First, generate self-signed certificates:

```bash
./scripts/generate-certs.sh
```

Then start services with TLS enabled:

```bash
docker-compose -f docker-compose.yml -f docker-compose.tls.yml up -d
```

### 3. Verify Services

- PostgreSQL: `psql -h localhost -U postgres -d ai_taxi_model`
- Kafka: `docker exec -it kafka kafka-topics.sh --bootstrap-server localhost:9092 --list`
- OpenSearch: `curl http://localhost:9200/_cluster/health`
- OpenSearch Dashboards: http://localhost:5601
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin)
- etcd: `docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 endpoint health`

## TLS Configuration

### Generating Certificates

The `scripts/generate-certs.sh` script generates self-signed certificates for both Kafka and OpenSearch:

```bash
./scripts/generate-certs.sh
```

This creates:
- **Kafka**: JKS keystore and truststore in `certs/kafka/`
- **OpenSearch**: PEM certificates for HTTP and Transport in `certs/opensearch/`
- **Root CA**: `certs/root-ca.pem` and `certs/root-ca.key`

### Enabling TLS

#### Option 1: Using docker-compose override

```bash
docker-compose -f docker-compose.yml -f docker-compose.tls.yml up -d
```

#### Option 2: Using environment variables

Create a `.env` file:

```bash
cp .env.example .env
# Edit .env and set:
# KAFKA_USE_TLS=true
# OPENSEARCH_USE_TLS=true
```

Then start services:

```bash
docker-compose up -d
```

### TLS Endpoints

When TLS is enabled:
- **Kafka SSL**: `localhost:9094`
- **OpenSearch HTTPS**: `https://localhost:9200`
- **OpenSearch Dashboards**: `https://localhost:5601`

## Service Details

### PostgreSQL

- **Host**: localhost
- **Port**: 5432
- **User**: postgres
- **Password**: postgres
- **Database**: ai_taxi_model

### Kafka

- **PLAINTEXT**: localhost:9092
- **SSL**: localhost:9094 (when TLS enabled)
- **KRaft Mode**: Single node, no Zookeeper required
- **Topics**: Create topics using `kafka-topics.sh`

Example:
```bash
docker exec -it kafka kafka-topics.sh --create \
  --bootstrap-server localhost:9092 \
  --topic taxi-trips \
  --partitions 3 \
  --replication-factor 1
```

### OpenSearch

- **HTTP**: http://localhost:9200
- **HTTPS**: https://localhost:9200 (when TLS enabled)
- **Security**: Disabled by default (set `OPENSEARCH_DISABLE_SECURITY=false` to enable)

When security is enabled:
- Default username: `admin`
- Default password: `admin` (change in production!)

**Prometheus Metrics**:
- OpenSearch exposes Prometheus metrics at `/_prometheus/metrics` endpoint
- Requires the Prometheus exporter plugin to be installed
- To install the plugin, run:
  ```bash
  docker exec -it opensearch ./bin/opensearch-plugin install \
    https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/3.3.2.0/prometheus-exporter-3.3.2.0.zip
  docker-compose restart opensearch
  ```
- Prometheus is configured to scrape OpenSearch metrics automatically (see `prometheus/prometheus.yml`)

### OpenSearch Dashboards

- **URL**: http://localhost:5601
- **Default credentials**: admin/admin (when security enabled)

### Prometheus

- **URL**: http://localhost:9090
- **Configuration**: `prometheus/prometheus.yml`
- **Data retention**: 200 hours

### Grafana

- **URL**: http://localhost:3000
- **Default credentials**: admin/admin
- **Prometheus datasource**: Pre-configured
- **Dashboards**: Located in `grafana/dashboards/`
  - **Taxi Monitor, OpenSearch & Database Metrics Dashboard**: Automatically provisioned
    - File processing metrics (files processed, records processed, errors)
    - OpenSearch indexing metrics (documents indexed, bulk operations, errors)
    - Database processing metrics (files processed, records inserted, tables created, batch operations, processing time, errors)
    - Real-time monitoring with 10-second refresh

## Environment Variables

Create a `.env` file to customize configuration:

```bash
# TLS Configuration
USE_TLS=false
KAFKA_USE_TLS=false
OPENSEARCH_USE_TLS=false

# OpenSearch Security
OPENSEARCH_DISABLE_SECURITY=false
```

## Data Persistence

All service data is persisted in Docker volumes:
- `postgres_data`: PostgreSQL data
- `kafka_data`: Kafka logs
- `opensearch_data`: OpenSearch indices
- `opensearch-dashboards_data`: Dashboards configuration
- `prometheus_data`: Prometheus metrics
- `grafana_data`: Grafana dashboards and settings
- `etcd_data`: etcd key-value store data

## Stopping Services

```bash
docker-compose down
```

To remove volumes (deletes all data):

```bash
docker-compose down -v
```

## Troubleshooting

### Kafka not starting

Check logs:
```bash
docker logs kafka
```

Ensure certificates exist if using TLS:
```bash
ls -la certs/kafka/
```

### OpenSearch not starting

Check logs:
```bash
docker logs opensearch
```

If using TLS, ensure certificates exist:
```bash
ls -la certs/opensearch/
```

### Certificate errors

Regenerate certificates:
```bash
rm -rf certs/
./scripts/generate-certs.sh
docker-compose restart
```

## Production Considerations

⚠️ **Warning**: This setup uses self-signed certificates and default passwords. For production:

1. Use proper CA-signed certificates
2. Change all default passwords
3. Enable OpenSearch security
4. Configure proper network isolation
5. Set up proper backup strategies
6. Configure resource limits
7. Use secrets management
8. Enable authentication for all services

## etcd Configuration Management

### Overview

etcd is a distributed key-value store that can be used to store application configuration parameters. The application configuration can be stored in etcd and retrieved at runtime.

### Populating Configuration

To populate etcd with application configuration parameters:

```bash
# Populate with default configuration
./scripts/populate-etcd-config.sh

# Populate with development profile configuration
./scripts/populate-etcd-config.sh dev

# Populate with production profile configuration
./scripts/populate-etcd-config.sh prod
```

### Viewing Configuration

To list all configuration parameters stored in etcd:

```bash
./scripts/list-etcd-config.sh
```

To view a specific configuration key:

```bash
docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 get "/ai-taxi-model/config/taxi.monitor.enabled"
```

### Deleting Configuration

To delete all configuration parameters from etcd:

```bash
./scripts/delete-etcd-config.sh
```

### Configuration Keys

All configuration keys are stored under the prefix `/ai-taxi-model/config/`:

- **Taxi Monitor**: `taxi.monitor.enabled`, `taxi.monitor.input.dir`, `taxi.monitor.error.dir`, `taxi.monitor.processed.dir`
- **Parquet Monitor**: `parquet.monitor.enabled`, `parquet.monitor.input.dir`, `parquet.monitor.error.dir`, `parquet.monitor.processed.dir`, `parquet.monitor.batch.size`, `parquet.monitor.batch.timer.seconds`, `parquet.database.batch.size`
- **OpenSearch**: `opensearch.host`, `opensearch.port`, `opensearch.scheme`, `opensearch.username`, `opensearch.password`
- **Database**: `db.url`, `db.username`, `db.password`, `db.schema`, `db.ssl.enabled`, `db.ssl.mode`
- **Quarkus**: `quarkus.http.port`, `quarkus.http.host`, `quarkus.log.level`, `quarkus.micrometer.export.prometheus.enabled`, `quarkus.scheduler.enabled`

### Using etcd Configuration in Application

To use etcd configuration in your Quarkus application, you would need to:

1. Add etcd client dependency to `pom.xml`
2. Configure Quarkus to read from etcd (using MicroProfile Config or custom configuration source)
3. Access configuration values using `@ConfigProperty` annotations

**Note**: Currently, the application reads configuration from `application.properties` files. To use etcd, you would need to implement a custom configuration source or use a library that provides etcd integration.

### etcd Service Details

- **Client API**: `localhost:2379`
- **Peer Communication**: `localhost:2380`
- **Data Persistence**: Stored in `etcd_data` Docker volume
- **Health Check**: Monitors etcd health status

## Network

All services are on the `ai-taxi-network` bridge network and can communicate using service names:
- `postgres`
- `kafka`
- `opensearch`
- `opensearch-dashboards`
- `prometheus`
- `grafana`
- `etcd`

