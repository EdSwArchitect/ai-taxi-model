#!/bin/bash

# Script to populate etcd with application configuration parameters
# Usage: ./scripts/populate-etcd-config.sh [profile]
# Profile can be: dev, prod, or default (no profile)

set -e

ETCD_HOST=${ETCD_HOST:-localhost}
ETCD_PORT=${ETCD_PORT:-2379}
ETCD_ENDPOINT="http://${ETCD_HOST}:${ETCD_PORT}"
ETCD_PREFIX="/ai-taxi-model/config"

PROFILE=${1:-default}

echo "Populating etcd with configuration for profile: $PROFILE"
echo "etcd endpoint: $ETCD_ENDPOINT"
echo ""

# Function to set a key-value pair in etcd
set_etcd_key() {
    local key=$1
    local value=$2
    echo "Setting $key = $value"
    docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 put "$key" "$value" > /dev/null 2>&1 || {
        echo "Error: Failed to set $key. Is etcd running?"
        exit 1
    }
}

# Check if etcd is running
if ! docker ps | grep -q etcd; then
    echo "Error: etcd container is not running. Please start it first:"
    echo "  docker-compose up -d etcd"
    exit 1
fi

# Check if etcd is healthy
if ! docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 endpoint health > /dev/null 2>&1; then
    echo "Error: etcd is not healthy. Please check the etcd container."
    exit 1
fi

echo "etcd is healthy. Proceeding with configuration..."

# Taxi Monitor Configuration
set_etcd_key "${ETCD_PREFIX}/taxi.monitor.enabled" "true"
set_etcd_key "${ETCD_PREFIX}/taxi.monitor.input.dir" "./data/input"
set_etcd_key "${ETCD_PREFIX}/taxi.monitor.error.dir" "./data/error"
set_etcd_key "${ETCD_PREFIX}/taxi.monitor.processed.dir" "./data/processed"

# Parquet File Directory Monitor Configuration
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.enabled" "true"
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.input.dir" "./data/parquet-input"
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.error.dir" "./data/parquet-error"
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.processed.dir" "./data/parquet-processed"
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.batch.size" "10"
set_etcd_key "${ETCD_PREFIX}/parquet.monitor.batch.timer.seconds" "30"
set_etcd_key "${ETCD_PREFIX}/parquet.database.batch.size" "1000"

# OpenSearch Configuration
if [ "$PROFILE" = "prod" ]; then
    set_etcd_key "${ETCD_PREFIX}/opensearch.host" "opensearch"
else
    set_etcd_key "${ETCD_PREFIX}/opensearch.host" "localhost"
fi
set_etcd_key "${ETCD_PREFIX}/opensearch.port" "9200"
set_etcd_key "${ETCD_PREFIX}/opensearch.scheme" "https"
set_etcd_key "${ETCD_PREFIX}/opensearch.username" "admin"
set_etcd_key "${ETCD_PREFIX}/opensearch.password" "admin"

# OpenSearch Bulk Indexing Rate Limiting
set_etcd_key "${ETCD_PREFIX}/opensearch.bulk.index.delay.ms" "100"
set_etcd_key "${ETCD_PREFIX}/opensearch.bulk.index.max.concurrent" "2"

# GeoMesa Configuration
set_etcd_key "${ETCD_PREFIX}/geomesa.ingestion.enabled" "true"
set_etcd_key "${ETCD_PREFIX}/geomesa.datastore.type" "filesystem"
if [ "$PROFILE" = "prod" ]; then
    set_etcd_key "${ETCD_PREFIX}/geomesa.filesystem.path" "/var/lib/geomesa"
    set_etcd_key "${ETCD_PREFIX}/geomesa.hbase.zookeepers" "hbase:2181"
else
    set_etcd_key "${ETCD_PREFIX}/geomesa.filesystem.path" "./data/geomesa"
    set_etcd_key "${ETCD_PREFIX}/geomesa.hbase.zookeepers" "localhost:2181"
fi
set_etcd_key "${ETCD_PREFIX}/geomesa.hbase.catalog" "geomesa"
set_etcd_key "${ETCD_PREFIX}/geomesa.ingestion.batch.size" "1000"

# Database Configuration
if [ "$PROFILE" = "prod" ]; then
    set_etcd_key "${ETCD_PREFIX}/db.url" "jdbc:postgresql://postgres:5432/ai_taxi_model"
else
    set_etcd_key "${ETCD_PREFIX}/db.url" "jdbc:postgresql://localhost:5432/ai_taxi_model"
fi
set_etcd_key "${ETCD_PREFIX}/db.username" "postgres"
set_etcd_key "${ETCD_PREFIX}/db.password" "postgres"
set_etcd_key "${ETCD_PREFIX}/db.schema" "public"
set_etcd_key "${ETCD_PREFIX}/db.ssl.enabled" "false"
set_etcd_key "${ETCD_PREFIX}/db.ssl.mode" "require"

# Quarkus HTTP Server Configuration
set_etcd_key "${ETCD_PREFIX}/quarkus.http.port" "8080"
set_etcd_key "${ETCD_PREFIX}/quarkus.http.host" "0.0.0.0"

# Quarkus Logging Configuration
if [ "$PROFILE" = "dev" ]; then
    set_etcd_key "${ETCD_PREFIX}/quarkus.log.level" "INFO"
    set_etcd_key "${ETCD_PREFIX}/quarkus.log.category.com.bscllc.ai.text.model.level" "DEBUG"
else
    set_etcd_key "${ETCD_PREFIX}/quarkus.log.level" "INFO"
    set_etcd_key "${ETCD_PREFIX}/quarkus.log.category.com.bscllc.ai.text.model.level" "INFO"
fi

# Quarkus Micrometer Prometheus
set_etcd_key "${ETCD_PREFIX}/quarkus.micrometer.export.prometheus.enabled" "true"
set_etcd_key "${ETCD_PREFIX}/quarkus.micrometer.export.prometheus.path" "/q/metrics"

# Quarkus Scheduler
set_etcd_key "${ETCD_PREFIX}/quarkus.scheduler.enabled" "true"

echo ""
echo "Configuration successfully populated to etcd!"
echo ""
echo "To view all configuration keys:"
echo "  docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 get --prefix \"${ETCD_PREFIX}/\""
echo ""
echo "To view a specific key:"
echo "  docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 get \"${ETCD_PREFIX}/taxi.monitor.enabled\""
echo ""
echo "To delete all configuration:"
echo "  docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 del --prefix \"${ETCD_PREFIX}/\""

