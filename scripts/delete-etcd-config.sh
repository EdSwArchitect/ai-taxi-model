#!/bin/bash

# Script to delete all application configuration parameters from etcd
# Usage: ./scripts/delete-etcd-config.sh

set -e

ETCD_HOST=${ETCD_HOST:-localhost}
ETCD_PORT=${ETCD_PORT:-2379}
ETCD_ENDPOINT="http://${ETCD_HOST}:${ETCD_PORT}"
ETCD_PREFIX="/ai-taxi-model/config"

echo "Deleting configuration from etcd..."
echo "etcd endpoint: $ETCD_ENDPOINT"
echo ""

# Check if etcd is running
if ! docker ps | grep -q etcd; then
    echo "Error: etcd container is not running."
    exit 1
fi

# Confirm deletion
read -p "Are you sure you want to delete all configuration keys under ${ETCD_PREFIX}/? (yes/no): " confirm
if [ "$confirm" != "yes" ]; then
    echo "Deletion cancelled."
    exit 0
fi

# Delete all keys with the prefix
docker exec -e ETCDCTL_API=3 etcd etcdctl --endpoints=http://localhost:2379 del --prefix "${ETCD_PREFIX}/"

echo ""
echo "Configuration deleted successfully!"
echo ""
echo "To repopulate configuration:"
echo "  ./scripts/populate-etcd-config.sh [profile]"

