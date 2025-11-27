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
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Service for interacting with OpenSearch.
 */
@ApplicationScoped
public class OpenSearchService {

    private static final Logger logger = LogManager.getLogger(OpenSearchService.class);

    private RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

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
                } else {
                    logger.warn("Bulk index operation had errors: {}", response.getStatusLine().getStatusCode());
                }
            }
        } catch (Exception e) {
            logger.error("Error executing bulk index operation", e);
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
                    return id;
                } else {
                    throw new IOException("Failed to index document: " + response.getStatusLine().getStatusCode());
                }
            }
        } catch (Exception e) {
            logger.error("Error indexing document to {}", index, e);
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

