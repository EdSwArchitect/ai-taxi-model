package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Service for interacting with OpenSearch.
 */
@ApplicationScoped
public class OpenSearchService {

    private static final Logger logger = LogManager.getLogger(OpenSearchService.class);

    private MeterRegistry meterRegistry;
    private RestClient restClient;
    private volatile boolean metricsInitialized = false;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Micrometer metrics
    private Counter documentsIndexedCounter;
    private Counter yellowTaxiDocumentsCounter;
    private Counter greenTaxiDocumentsCounter;
    private Counter indexingErrorsCounter;
    private Counter bulkOperationsCounter;
    private Counter yellowTaxiBulkOperationsCounter;
    private Counter greenTaxiBulkOperationsCounter;

    /**
     * Lazily gets the MeterRegistry from CDI container if available.
     * Returns null if CDI is not available or MeterRegistry bean is not found.
     */
    private MeterRegistry getMeterRegistry() {
        if (meterRegistry != null) {
            return meterRegistry;
        }
        
        try {
            ArcContainer container = Arc.container();
            if (container != null) {
                meterRegistry = container.instance(MeterRegistry.class).get();
                return meterRegistry;
            }
        } catch (Exception e) {
            logger.debug("MeterRegistry not available from CDI container", e);
        }
        
        return null;
    }
    
    @PostConstruct
    void initMetrics() {
        try {
            // Try to get MeterRegistry from CDI container
            MeterRegistry registry = getMeterRegistry();
            if (registry == null) {
                logger.debug("MeterRegistry is not available. Metrics will not be collected.");
                return;
            }
            
            documentsIndexedCounter = Counter.builder("opensearch.documents.indexed")
                    .description("Total number of documents indexed to OpenSearch")
                    .register(registry);
            
            yellowTaxiDocumentsCounter = Counter.builder("opensearch.yellow.documents")
                    .description("Number of yellow taxi documents indexed")
                    .register(registry);
            
            greenTaxiDocumentsCounter = Counter.builder("opensearch.green.documents")
                    .description("Number of green taxi documents indexed")
                    .register(registry);
            
            indexingErrorsCounter = Counter.builder("opensearch.indexing.errors")
                    .description("Number of indexing errors")
                    .register(registry);
            
            bulkOperationsCounter = Counter.builder("opensearch.bulk.operations")
                    .description("Number of bulk index operations (can represent files processed)")
                    .register(registry);
            
            yellowTaxiBulkOperationsCounter = Counter.builder("opensearch.yellow.bulk.operations")
                    .description("Number of bulk index operations for yellow taxi")
                    .register(registry);
            
            greenTaxiBulkOperationsCounter = Counter.builder("opensearch.green.bulk.operations")
                    .description("Number of bulk index operations for green taxi")
                    .register(registry);
            
            metricsInitialized = true;
            logger.debug("Metrics initialized successfully");
        } catch (Exception e) {
            logger.warn("Failed to initialize metrics. Metrics will not be collected.", e);
            metricsInitialized = false;
        }
    }

    void onStart(@Observes StartupEvent ev) {
        String host = System.getProperty("opensearch.host", "localhost");
        int port = Integer.parseInt(System.getProperty("opensearch.port", "9200"));
        String scheme = System.getProperty("opensearch.scheme", "http");
        String username = System.getProperty("opensearch.username", "admin");
        String password = System.getProperty("opensearch.password", "SuperSecret123!");

        logger.info("Initializing OpenSearch client: {}://{}:{}", scheme, host, port);

        // Create credentials provider
        CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username, password)
        );

        // Create REST client
        RestClientBuilder builder = RestClient.builder(
            new HttpHost(host, port, scheme)
        );
        builder.setHttpClientConfigCallback(httpClientBuilder ->
            httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
        );
        this.restClient = builder.build();

        logger.info("OpenSearch client initialized successfully");
    }

    /**
     * Performs a bulk index operation using REST API directly.
     *
     * @param index the index name
     * @param documents list of documents to index
     */
    public void bulkIndex(String index, List<Map<String, Object>> documents) {
        try {
            StringBuilder bulkBody = new StringBuilder();
            
            for (Map<String, Object> doc : documents) {
                // Add index action
                bulkBody.append("{\"index\":{\"_index\":\"").append(index).append("\"}}\n");
                // Add document
                bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
            }

            org.apache.http.entity.StringEntity entity = new org.apache.http.entity.StringEntity(
                bulkBody.toString(),
                org.apache.http.entity.ContentType.APPLICATION_JSON
            );

            org.apache.http.client.methods.HttpPost request = new org.apache.http.client.methods.HttpPost(
                "/_bulk"
            );
            request.setEntity(entity);

            try (org.apache.http.client.methods.CloseableHttpResponse response = 
                    org.apache.http.impl.client.HttpClients.createDefault().execute(request)) {
                if (response.getStatusLine().getStatusCode() >= 200 && 
                    response.getStatusLine().getStatusCode() < 300) {
                    logger.info("Bulk index operation successful: {} items indexed", documents.size());
                    
                    // Update metrics (if available)
                    if (metricsInitialized && bulkOperationsCounter != null) {
                        bulkOperationsCounter.increment();
                        documentsIndexedCounter.increment(documents.size());
                        if ("yellowtaxi".equals(index)) {
                            yellowTaxiBulkOperationsCounter.increment();
                            yellowTaxiDocumentsCounter.increment(documents.size());
                        } else if ("greentaxi".equals(index)) {
                            greenTaxiBulkOperationsCounter.increment();
                            greenTaxiDocumentsCounter.increment(documents.size());
                        }
                    }
                } else {
                    logger.warn("Bulk index operation had errors: {}", response.getStatusLine().getStatusCode());
                    if (metricsInitialized && indexingErrorsCounter != null) {
                        indexingErrorsCounter.increment();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error executing bulk index operation", e);
            if (metricsInitialized && indexingErrorsCounter != null) {
                indexingErrorsCounter.increment();
            }
            throw new RuntimeException("Failed to execute bulk index", e);
        }
    }

    /**
     * Indexes a single document.
     *
     * @param index the index name
     * @param document the document to index
     * @return the document ID
     */
    public String indexDocument(String index, Map<String, Object> document) {
        try {
            org.apache.http.entity.StringEntity entity = new org.apache.http.entity.StringEntity(
                objectMapper.writeValueAsString(document),
                org.apache.http.entity.ContentType.APPLICATION_JSON
            );

            org.apache.http.client.methods.HttpPost request = new org.apache.http.client.methods.HttpPost(
                "/" + index + "/_doc"
            );
            request.setEntity(entity);

            try (org.apache.http.client.methods.CloseableHttpResponse response = 
                    org.apache.http.impl.client.HttpClients.createDefault().execute(request)) {
                if (response.getStatusLine().getStatusCode() >= 200 && 
                    response.getStatusLine().getStatusCode() < 300) {
                    String responseBody = new String(response.getEntity().getContent().readAllBytes());
                    Map<String, Object> result = objectMapper.readValue(responseBody, Map.class);
                    String id = (String) result.get("_id");
                    logger.debug("Indexed document to {} with ID: {}", index, id);
                    
                    // Update metrics (if available)
                    if (metricsInitialized && documentsIndexedCounter != null) {
                        documentsIndexedCounter.increment();
                        if ("yellowtaxi".equals(index) && yellowTaxiDocumentsCounter != null) {
                            yellowTaxiDocumentsCounter.increment();
                        } else if ("greentaxi".equals(index) && greenTaxiDocumentsCounter != null) {
                            greenTaxiDocumentsCounter.increment();
                        }
                    }
                    
                    return id;
                } else {
                    if (metricsInitialized && indexingErrorsCounter != null) {
                        indexingErrorsCounter.increment();
                    }
                    throw new IOException("Failed to index document: " + response.getStatusLine().getStatusCode());
                }
            }
        } catch (Exception e) {
            logger.error("Error indexing document to {}", index, e);
            if (metricsInitialized && indexingErrorsCounter != null) {
                indexingErrorsCounter.increment();
            }
            throw new RuntimeException("Failed to index document", e);
        }
    }

    /**
     * Closes the OpenSearch client.
     */
    public void close() {
        if (restClient != null) {
            try {
                restClient.close();
            } catch (IOException e) {
                logger.error("Error closing OpenSearch client", e);
            }
        }
    }
}

