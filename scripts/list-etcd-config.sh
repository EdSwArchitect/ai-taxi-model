#!/bin/bash

# Script to list all application configuration parameters from etcd
# Usage: ./scripts/list-etcd-config.sh

set -e

ETCD_HOST=${ETCD_HOST:-localhost}
ETCD_PORT=${ETCD_PORT:-2379}
ETCD_ENDPOINT="http://${ETCD_HOST}:${ETCD_PORT}"
ETCD_PREFIX="/ai-taxi-model/config"

echo "Listing configuration from etcd..."
echo "etcd endpoint: $ETCD_ENDPOINT"
echo ""

# Check if etcd is running
if ! docker ps | grep -q etcd; then
    echo "Error: etcd container is not running. Please start it first:"
    echo "  docker-compose up -d etcd"
    exit 1
fi

# List all keys with their values
echo "Configuration keys and values:"
echo "=============================="
output=$(docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 get --prefix "${ETCD_PREFIX}/" --print-value-only=false 2>/dev/null)

if [ -z "$output" ]; then
    echo "No configuration found in etcd."
    echo "To populate configuration, run: ./scripts/populate-etcd-config.sh [profile]"
    exit 0
fi

# Parse key-value pairs (etcd returns key on one line, value on next line)
echo "$output" | awk '
BEGIN { key="" }
{
    if (key == "") {
        key = $0
    } else {
        value = $0
        print key " = " value
        key = ""
    }
}'

echo ""
echo "To get a specific key value:"
echo "  docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 get \"${ETCD_PREFIX}/<key>\""

