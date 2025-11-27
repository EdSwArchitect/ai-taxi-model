#!/bin/bash

# Script to generate self-signed certificates for Kafka and OpenSearch
# Usage: ./scripts/generate-certs.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CERTS_DIR="$PROJECT_ROOT/certs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Generating self-signed certificates for Kafka and OpenSearch...${NC}"

# Create certificates directory structure
mkdir -p "$CERTS_DIR/kafka"
mkdir -p "$CERTS_DIR/opensearch"

# Generate Root CA
echo -e "${YELLOW}Generating Root CA...${NC}"
openssl req -new -x509 -keyout "$CERTS_DIR/root-ca.key" -out "$CERTS_DIR/root-ca.pem" \
    -days 365 -nodes -subj "/C=US/ST=State/L=City/O=Organization/CN=RootCA"

# Generate Kafka certificates
echo -e "${YELLOW}Generating Kafka certificates...${NC}"

# Create keystore
keytool -genkeypair -alias kafka -keyalg RSA -keysize 2048 \
    -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
    -storepass changeit -keypass changeit \
    -validity 365 \
    -dname "CN=kafka,OU=OrgUnit,O=Organization,L=City,S=State,C=US" \
    -ext "SAN=DNS:kafka,DNS:localhost,IP:127.0.0.1"

# Create certificate signing request
keytool -certreq -alias kafka -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
    -storepass changeit -file "$CERTS_DIR/kafka/kafka.csr"

# Sign certificate with Root CA
openssl x509 -req -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca.key" \
    -in "$CERTS_DIR/kafka/kafka.csr" -out "$CERTS_DIR/kafka/kafka-signed.crt" \
    -days 365 -CAcreateserial

# Import Root CA into keystore
keytool -import -alias CARoot -file "$CERTS_DIR/root-ca.pem" \
    -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
    -storepass changeit -noprompt

# Import signed certificate
keytool -import -alias kafka -file "$CERTS_DIR/kafka/kafka-signed.crt" \
    -keystore "$CERTS_DIR/kafka/kafka.keystore.jks" \
    -storepass changeit -noprompt

# Create truststore
keytool -import -alias CARoot -file "$CERTS_DIR/root-ca.pem" \
    -keystore "$CERTS_DIR/kafka/kafka.truststore.jks" \
    -storepass changeit -noprompt

# Generate OpenSearch certificates
echo -e "${YELLOW}Generating OpenSearch certificates...${NC}"

# Generate OpenSearch keystore (JKS format)
keytool -genkeypair -alias localhost -keyalg RSA -keysize 2048 \
    -keystore "$CERTS_DIR/opensearch/opensearch-keystore.jks" \
    -storepass changeit -keypass changeit \
    -validity 365 \
    -dname "CN=localhost,OU=OrgUnit,O=Organization,L=City,S=State,C=US" \
    -ext "SAN=DNS:localhost,DNS:opensearch,IP:127.0.0.1"

# Create certificate signing request for OpenSearch
keytool -certreq -alias localhost -keystore "$CERTS_DIR/opensearch/opensearch-keystore.jks" \
    -storepass changeit -file "$CERTS_DIR/opensearch/opensearch.csr"

# Sign OpenSearch certificate with Root CA
openssl x509 -req -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca.key" \
    -in "$CERTS_DIR/opensearch/opensearch.csr" -out "$CERTS_DIR/opensearch/opensearch-signed.crt" \
    -days 365 -CAcreateserial

# Import Root CA into OpenSearch keystore
keytool -import -alias CARoot -file "$CERTS_DIR/root-ca.pem" \
    -keystore "$CERTS_DIR/opensearch/opensearch-keystore.jks" \
    -storepass changeit -noprompt

# Import signed certificate into OpenSearch keystore
keytool -import -alias localhost -file "$CERTS_DIR/opensearch/opensearch-signed.crt" \
    -keystore "$CERTS_DIR/opensearch/opensearch-keystore.jks" \
    -storepass changeit -noprompt

# Create OpenSearch truststore
keytool -import -alias CARoot -file "$CERTS_DIR/root-ca.pem" \
    -keystore "$CERTS_DIR/opensearch/truststore.jks" \
    -storepass changeit -noprompt

# Also generate PEM files for compatibility (optional)
# Generate HTTP certificate (PEM format)
openssl genrsa -out "$CERTS_DIR/opensearch/http-key.pem" 2048
openssl req -new -key "$CERTS_DIR/opensearch/http-key.pem" \
    -out "$CERTS_DIR/opensearch/http.csr" \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
openssl x509 -req -in "$CERTS_DIR/opensearch/http.csr" \
    -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca.key" \
    -out "$CERTS_DIR/opensearch/http.pem" -days 365 -CAcreateserial

# Generate Transport certificate (PEM format)
openssl genrsa -out "$CERTS_DIR/opensearch/transport-key.pem" 2048
openssl req -new -key "$CERTS_DIR/opensearch/transport-key.pem" \
    -out "$CERTS_DIR/opensearch/transport.csr" \
    -subj "/C=US/ST=State/L=City/O=Organization/CN=localhost"
openssl x509 -req -in "$CERTS_DIR/opensearch/transport.csr" \
    -CA "$CERTS_DIR/root-ca.pem" -CAkey "$CERTS_DIR/root-ca.key" \
    -out "$CERTS_DIR/opensearch/transport.pem" -days 365 -CAcreateserial

# Copy root CA to OpenSearch directory
cp "$CERTS_DIR/root-ca.pem" "$CERTS_DIR/opensearch/root-ca.pem"

# Set proper permissions
chmod 644 "$CERTS_DIR/kafka"/*.jks
chmod 644 "$CERTS_DIR/opensearch"/*.jks
chmod 644 "$CERTS_DIR/opensearch"/*.pem
chmod 600 "$CERTS_DIR/opensearch"/*-key.pem
chmod 600 "$CERTS_DIR/root-ca.key"

# Clean up temporary files
rm -f "$CERTS_DIR/kafka/kafka.csr" "$CERTS_DIR/kafka/kafka-signed.crt"
rm -f "$CERTS_DIR/opensearch/opensearch.csr" "$CERTS_DIR/opensearch/opensearch-signed.crt"
rm -f "$CERTS_DIR/opensearch/http.csr" "$CERTS_DIR/opensearch/transport.csr"
rm -f "$CERTS_DIR/root-ca.srl"

echo -e "${GREEN}Certificates generated successfully!${NC}"
echo -e "${YELLOW}Certificate locations:${NC}"
echo "  Kafka keystore: $CERTS_DIR/kafka/kafka.keystore.jks"
echo "  Kafka truststore: $CERTS_DIR/kafka/kafka.truststore.jks"
echo "  OpenSearch keystore: $CERTS_DIR/opensearch/opensearch-keystore.jks"
echo "  OpenSearch truststore: $CERTS_DIR/opensearch/truststore.jks"
echo "  OpenSearch HTTP cert (PEM): $CERTS_DIR/opensearch/http.pem"
echo "  OpenSearch Transport cert (PEM): $CERTS_DIR/opensearch/transport.pem"
echo "  Root CA: $CERTS_DIR/root-ca.pem"

