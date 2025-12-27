package com.bscllc.ai.text.model.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
    private CloseableHttpClient httpClient;
    private HttpHost httpHost;
    private CredentialsProvider credentialsProvider;
    private HttpClientContext httpContext;
    private String authHeader; // Pre-computed Authorization header
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
    
    // Semaphore to limit concurrent bulk operations
    private Semaphore bulkOperationSemaphore;

    /**
     * Lazily gets the MeterRegistry from CDI container if available.
     * Returns null if CDI is not available or MeterRegistry bean is not found.
     * This method is safe to call from any thread context.
     */
    private MeterRegistry getMeterRegistry() {
        if (meterRegistry != null) {
            return meterRegistry;
        }
        
        try {
            // Check if Arc container is available
            if (!Arc.container().isRunning()) {
                return null;
            }
            
            ArcContainer container = Arc.container();
            if (container != null) {
                var instance = container.instance(MeterRegistry.class);
                if (instance.isAvailable()) {
                    meterRegistry = instance.get();
                    return meterRegistry;
                }
            }
        } catch (Exception e) {
            // Arc might not be available in this context (e.g., worker threads)
            logger.debug("MeterRegistry not available from CDI container: {}", e.getMessage());
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

    @ConfigProperty(name = "opensearch.host", defaultValue = "localhost")
    String opensearchHost;

    @ConfigProperty(name = "opensearch.port", defaultValue = "9200")
    int opensearchPort;

    @ConfigProperty(name = "opensearch.scheme", defaultValue = "https")
    String opensearchScheme;

    @ConfigProperty(name = "opensearch.username", defaultValue = "admin")
    String opensearchUsername;

    @ConfigProperty(name = "opensearch.password", defaultValue = "admin")
    String opensearchPassword;

    @ConfigProperty(name = "opensearch.bulk.index.delay.ms", defaultValue = "100")
    long bulkIndexDelayMs;

    @ConfigProperty(name = "opensearch.bulk.index.max.concurrent", defaultValue = "2")
    int maxConcurrentBulkOperations;

    void onStart(@Observes StartupEvent ev) {
        String host = opensearchHost;
        int port = opensearchPort;
        String scheme = opensearchScheme;
        String username = opensearchUsername;
        String password = opensearchPassword;

        logger.info("Initializing OpenSearch client: {}://{}:{}", scheme, host, port);

        // Create HTTP host first (needed for auth cache)
        this.httpHost = new HttpHost(host, port, scheme);

        // Create credentials provider
        this.credentialsProvider = new BasicCredentialsProvider();
        this.credentialsProvider.setCredentials(
            AuthScope.ANY,
            new UsernamePasswordCredentials(username, password)
        );

        // Pre-compute Authorization header for Basic authentication
        // This ensures the header is always sent with requests
        String credentials = username + ":" + password;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        this.authHeader = "Basic " + encodedCredentials;
        
        logger.debug("Configured Basic authentication for user: {}", username);

        // Create HTTP context for authentication with pre-emptive Basic auth
        this.httpContext = HttpClientContext.create();
        this.httpContext.setCredentialsProvider(this.credentialsProvider);
        
        // Pre-emptively add Basic auth scheme to avoid 401 challenge-response
        // This ensures the Authorization header is sent with every request
        BasicScheme basicAuth = new BasicScheme();
        org.apache.http.impl.client.BasicAuthCache authCache = new org.apache.http.impl.client.BasicAuthCache();
        authCache.put(this.httpHost, basicAuth);
        this.httpContext.setAuthCache(authCache);
        
        // Create HTTP client with SSL support
        CloseableHttpClient client;
        if ("https".equalsIgnoreCase(scheme)) {
            // For HTTPS, configure SSL to accept self-signed certificates
            try {
                // Create a trust manager that accepts all certificates
                TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        @Override
                        public void checkClientTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        }
                        @Override
                        public void checkServerTrusted(
                            java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
                };
                
                // Create SSL context with trust-all manager
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
                
                // Create SSL socket factory that doesn't verify hostnames
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext,
                    NoopHostnameVerifier.INSTANCE
                );
                
                client = org.apache.http.impl.client.HttpClients.custom()
                    .setSSLSocketFactory(sslSocketFactory)
                    .setDefaultCredentialsProvider(this.credentialsProvider)
                    .build();
                
                logger.info("Configured HTTP client with SSL (accepting self-signed certificates)");
            } catch (Exception e) {
                logger.error("Failed to configure SSL context, falling back to default HTTP client", e);
                client = org.apache.http.impl.client.HttpClients.custom()
                    .setDefaultCredentialsProvider(this.credentialsProvider)
                    .build();
            }
        } else {
            // For HTTP, use standard client
            client = org.apache.http.impl.client.HttpClients.custom()
                .setDefaultCredentialsProvider(this.credentialsProvider)
                .build();
        }
        
        this.httpClient = client;
        
        // Create REST client with credentials (Note: RestClient is not currently used, but kept for future use)
        // The direct HTTP client (httpClient) is used for all requests and has SSL and authentication configured above
        RestClientBuilder builder = RestClient.builder(this.httpHost);
        builder.setHttpClientConfigCallback(httpClientBuilder -> {
            // Set credentials provider for authentication
            httpClientBuilder.setDefaultCredentialsProvider(this.credentialsProvider);
            // Note: SSL configuration for RestClient would require additional async client setup,
            // but since RestClient is not used (all requests go through httpClient above),
            // we just ensure credentials are set. The httpClient already has SSL properly configured.
            return httpClientBuilder;
        });
        this.restClient = builder.build();
        
        // Initialize semaphore for rate limiting concurrent bulk operations
        this.bulkOperationSemaphore = new Semaphore(maxConcurrentBulkOperations, true);

        logger.info("OpenSearch client initialized successfully");
        logger.info("Bulk indexing configuration: delay={}ms, maxConcurrent={}", bulkIndexDelayMs, maxConcurrentBulkOperations);
    }

    /**
     * Performs a bulk index operation using REST API directly.
     * Rate limiting is applied via semaphore and delay to prevent overwhelming OpenSearch
     * and avoid thread blocking issues. This method should be called from worker threads
     * (e.g., from @Scheduled methods which run on worker threads by default).
     *
     * @param index the index name
     * @param documents list of documents to index
     */
    public void bulkIndex(String index, List<Map<String, Object>> documents) {
        // Acquire semaphore to limit concurrent operations
        try {
            if (!bulkOperationSemaphore.tryAcquire(30, TimeUnit.SECONDS)) {
                logger.warn("Timeout waiting for bulk operation semaphore. Index: {}, documents: {}", index, documents.size());
                if (metricsInitialized && indexingErrorsCounter != null) {
                    indexingErrorsCounter.increment();
                }
                throw new RuntimeException("Timeout waiting for bulk operation slot");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for bulk operation semaphore", e);
            if (metricsInitialized && indexingErrorsCounter != null) {
                indexingErrorsCounter.increment();
            }
            throw new RuntimeException("Interrupted while waiting for bulk operation", e);
        }
        
        try {
            // Add delay before indexing to prevent overwhelming OpenSearch
            if (bulkIndexDelayMs > 0) {
                try {
                    Thread.sleep(bulkIndexDelayMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Interrupted during bulk index delay", e);
                }
            }
            
            StringBuilder bulkBody = new StringBuilder();
            
            for (Map<String, Object> doc : documents) {
                // Add index action
                bulkBody.append("{\"index\":{\"_index\":\"").append(index).append("\"}}\n");
                // Add document
                bulkBody.append(objectMapper.writeValueAsString(doc)).append("\n");
            }

            StringEntity entity = new StringEntity(
                bulkBody.toString(),
                ContentType.APPLICATION_JSON
            );

            HttpPost request = new HttpPost("/_bulk");
            request.setEntity(entity);
            
            // Explicitly add Authorization header to ensure it's sent
            if (authHeader != null) {
                request.setHeader("Authorization", authHeader);
            }

            if (httpClient == null || httpHost == null) {
                throw new IllegalStateException("OpenSearch client not initialized. Call onStart() first.");
            }

            // Use HttpClientContext to ensure authentication is included
            try (CloseableHttpResponse response = httpClient.execute(httpHost, request, httpContext)) {
                if (response.getStatusLine().getStatusCode() >= 200 && 
                    response.getStatusLine().getStatusCode() < 300) {
                    logger.debug("Bulk index operation successful: {} items indexed to {}", documents.size(), index);
                    
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
                    logger.warn("Bulk index operation had errors: {} for index {}", response.getStatusLine().getStatusCode(), index);
                    if (metricsInitialized && indexingErrorsCounter != null) {
                        indexingErrorsCounter.increment();
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error executing bulk index operation for index: {}", index, e);
            if (metricsInitialized && indexingErrorsCounter != null) {
                indexingErrorsCounter.increment();
            }
            throw new RuntimeException("Failed to execute bulk index", e);
        } finally {
            // Always release semaphore
            bulkOperationSemaphore.release();
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
            StringEntity entity = new StringEntity(
                objectMapper.writeValueAsString(document),
                ContentType.APPLICATION_JSON
            );

            HttpPost request = new HttpPost("/" + index + "/_doc");
            request.setEntity(entity);
            
            // Explicitly add Authorization header to ensure it's sent
            if (authHeader != null) {
                request.setHeader("Authorization", authHeader);
            }

            if (httpClient == null || httpHost == null) {
                throw new IllegalStateException("OpenSearch client not initialized. Call onStart() first.");
            }

            // Use HttpClientContext to ensure authentication is included
            try (CloseableHttpResponse response = httpClient.execute(httpHost, request, httpContext)) {
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
        
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                logger.error("Error closing HTTP client", e);
            }
        }
    }
}

