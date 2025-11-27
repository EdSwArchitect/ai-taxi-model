# Docker Compose Setup

This directory contains a Docker Compose configuration for running the AI Taxi Model project with supporting services.

## Services

- **PostgreSQL 15**: Database server on port 5432
- **Kafka (KRaft)**: Apache Kafka with KRaft (no Zookeeper) on ports 9092 (PLAINTEXT) and 9094 (SSL)
- **OpenSearch 3.3.2**: Search and analytics engine on port 9200
- **OpenSearch Dashboards 3.3.2**: Visualization UI on port 5601
- **Prometheus**: Metrics collection on port 9090
- **Grafana 12.3.0**: Metrics visualization on port 3000

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

## Network

All services are on the `ai-taxi-network` bridge network and can communicate using service names:
- `postgres`
- `kafka`
- `opensearch`
- `opensearch-dashboards`
- `prometheus`
- `grafana`

