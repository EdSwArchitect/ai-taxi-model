package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Service for ingesting taxi trip data into GeoMesa/GeoServer.
 * Reads Parquet files and converts them to GeoMesa SimpleFeatures for spatial indexing.
 * 
 * NOTE: This service requires GeoMesa and GeoTools dependencies to be uncommented in pom.xml.
 * Currently disabled to allow project compilation. To enable:
 * 1. Uncomment GeoMesa and GeoTools dependencies in pom.xml
 * 2. Restore the full implementation (see git history)
 * 3. Configure GeoMesa repositories if needed
 */
@ApplicationScoped
public class GeoMesaIngestionService {

    private static final Logger logger = LogManager.getLogger(GeoMesaIngestionService.class);

    @ConfigProperty(name = "geomesa.datastore.type", defaultValue = "filesystem")
    String dataStoreType;

    @ConfigProperty(name = "geomesa.filesystem.path", defaultValue = "./data/geomesa")
    String filesystemPath;

    @ConfigProperty(name = "geomesa.hbase.catalog", defaultValue = "geomesa")
    String hbaseCatalog;

    @ConfigProperty(name = "geomesa.hbase.zookeepers", defaultValue = "localhost:2181")
    String hbaseZookeepers;

    @ConfigProperty(name = "geomesa.ingestion.batch.size", defaultValue = "1000")
    int batchSize;

    @ConfigProperty(name = "geomesa.ingestion.enabled", defaultValue = "true")
    boolean enabled;

    @PostConstruct
    void init() {
        if (!enabled) {
            logger.info("GeoMesa ingestion is disabled. Set geomesa.ingestion.enabled=true to enable.");
            return;
        }

        logger.warn("GeoMesa ingestion service is not fully implemented. " +
                "GeoMesa dependencies are commented out in pom.xml. " +
                "To enable, uncomment GeoMesa and GeoTools dependencies.");
    }

    /**
     * Ingests a Green Taxi Parquet file into GeoMesa.
     * Currently a stub - requires GeoMesa dependencies to be enabled.
     */
    public long ingestGreenTaxiFile(Path parquetFile) throws IOException {
        if (!enabled) {
            logger.warn("GeoMesa ingestion is disabled");
            return 0;
        }

        logger.warn("GeoMesa ingestion not available - dependencies not configured. File: {}", parquetFile);
        return 0;
    }

    /**
     * Ingests a Yellow Taxi Parquet file into GeoMesa.
     * Currently a stub - requires GeoMesa dependencies to be enabled.
     */
    public long ingestYellowTaxiFile(Path parquetFile) throws IOException {
        if (!enabled) {
            logger.warn("GeoMesa ingestion is disabled");
            return 0;
        }

        logger.warn("GeoMesa ingestion not available - dependencies not configured. File: {}", parquetFile);
        return 0;
    }
}
