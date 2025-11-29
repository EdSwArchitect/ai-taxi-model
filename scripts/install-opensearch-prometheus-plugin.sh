#!/bin/bash

# Script to install Prometheus exporter plugin in OpenSearch
# This must be run inside the OpenSearch container

set -e

echo "Installing OpenSearch Prometheus Exporter Plugin..."

# Download and install the plugin
./bin/opensearch-plugin install \
  https://github.com/opensearch-project/opensearch-prometheus-exporter/releases/download/3.3.2.0/prometheus-exporter-3.3.2.0.zip

echo "Plugin installed successfully!"
echo "Restart the OpenSearch container to apply changes:"
echo "  docker-compose restart opensearch"

