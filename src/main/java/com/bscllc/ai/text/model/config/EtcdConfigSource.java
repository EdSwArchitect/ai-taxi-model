package com.bscllc.ai.text.model.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.microprofile.config.spi.ConfigSource;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import jakarta.annotation.PreDestroy;

/**
 * Custom ConfigSource that reads configuration from etcd.
 * Falls back to properties files if etcd is not available.
 * 
 * Configuration keys in etcd should be stored under the prefix:
 * /ai-taxi-model/config/
 * 
 * For example: /ai-taxi-model/config/taxi.monitor.enabled
 */
public class EtcdConfigSource implements ConfigSource {

    private static final Logger logger = LogManager.getLogger(EtcdConfigSource.class);
    private static final String ETCD_PREFIX = "/ai-taxi-model/config/";
    private static final String ETCD_HOST_ENV = "ETCD_HOST";
    private static final String ETCD_PORT_ENV = "ETCD_PORT";
    private static final String DEFAULT_ETCD_HOST = "localhost";
    private static final String DEFAULT_ETCD_PORT = "2379";
    
    private Client etcdClient;
    private KV kvClient;
    private boolean etcdAvailable = false;
    private Map<String, String> cachedConfig = new HashMap<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_TTL_MS = 30000; // 30 seconds cache

    public EtcdConfigSource() {
        initializeEtcdClient();
    }

    private void initializeEtcdClient() {
        String etcdHost = System.getenv(ETCD_HOST_ENV);
        if (etcdHost == null || etcdHost.isEmpty()) {
            etcdHost = System.getProperty(ETCD_HOST_ENV, DEFAULT_ETCD_HOST);
        }
        
        String etcdPort = System.getenv(ETCD_PORT_ENV);
        if (etcdPort == null || etcdPort.isEmpty()) {
            etcdPort = System.getProperty(ETCD_PORT_ENV, DEFAULT_ETCD_PORT);
        }
        
        String etcdEndpoint = "http://" + etcdHost + ":" + etcdPort;
        
        try {
            logger.info("Attempting to connect to etcd at: {}", etcdEndpoint);
            etcdClient = Client.builder()
                    .endpoints(etcdEndpoint)
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            
            kvClient = etcdClient.getKVClient();
            
            // Test connection by trying to get a key
            ByteSequence testKey = ByteSequence.from("test".getBytes(StandardCharsets.UTF_8));
            kvClient.get(testKey).get(2, TimeUnit.SECONDS);
            
            etcdAvailable = true;
            logger.info("Successfully connected to etcd. Configuration will be loaded from etcd.");
            loadConfigurationFromEtcd();
        } catch (Exception e) {
            logger.warn("etcd is not available at {}. Falling back to properties files. Error: {}", etcdEndpoint, e.getMessage());
            etcdAvailable = false;
            if (etcdClient != null) {
                try {
                    etcdClient.close();
                } catch (Exception closeException) {
                    logger.debug("Error closing etcd client: {}", closeException.getMessage());
                }
                etcdClient = null;
                kvClient = null;
            }
        }
    }

    private void loadConfigurationFromEtcd() {
        if (!etcdAvailable || kvClient == null) {
            return;
        }
        
        try {
            String prefix = ETCD_PREFIX;
            ByteSequence prefixBytes = ByteSequence.from(prefix.getBytes(StandardCharsets.UTF_8));
            
            // Use withPrefix to get all keys with the prefix
            // Note: withPrefix is deprecated but still functional in jetcd 0.8.0
            @SuppressWarnings("deprecation")
            GetOption getOption = GetOption.builder()
                    .withPrefix(prefixBytes)
                    .build();
            
            GetResponse response = kvClient.get(prefixBytes, getOption)
                    .get(2, TimeUnit.SECONDS);
            
            Map<String, String> newConfig = new HashMap<>();
            for (KeyValue kv : response.getKvs()) {
                String key = kv.getKey().toString(StandardCharsets.UTF_8);
                String value = kv.getValue().toString(StandardCharsets.UTF_8);
                
                // Remove the prefix to get the actual config key
                if (key.startsWith(prefix)) {
                    String configKey = key.substring(prefix.length());
                    newConfig.put(configKey, value);
                    logger.debug("Loaded config from etcd: {} = {}", configKey, value);
                }
            }
            
            cachedConfig = newConfig;
            lastCacheUpdate = System.currentTimeMillis();
            logger.info("Loaded {} configuration keys from etcd", newConfig.size());
        } catch (Exception e) {
            logger.warn("Failed to load configuration from etcd: {}", e.getMessage());
            etcdAvailable = false;
        }
    }

    @Override
    public Map<String, String> getProperties() {
        if (!etcdAvailable) {
            return Map.of();
        }
        
        // Refresh cache if it's stale
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TTL_MS) {
            loadConfigurationFromEtcd();
        }
        
        return new HashMap<>(cachedConfig);
    }

    @Override
    public Set<String> getPropertyNames() {
        if (!etcdAvailable) {
            return Set.of();
        }
        
        // Refresh cache if it's stale
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TTL_MS) {
            loadConfigurationFromEtcd();
        }
        
        return cachedConfig.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        if (!etcdAvailable) {
            return null; // Return null to allow fallback to properties files
        }
        
        // Refresh cache if it's stale
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_TTL_MS) {
            loadConfigurationFromEtcd();
        }
        
        String value = cachedConfig.get(propertyName);
        if (value != null) {
            logger.debug("Retrieved config from etcd: {} = {}", propertyName, value);
        }
        return value;
    }

    @Override
    public String getName() {
        return "etcd";
    }

    @Override
    public int getOrdinal() {
        // Higher ordinal = higher priority
        // etcd should be checked before properties files (which typically have ordinal 100)
        // But lower than system properties (400) and environment variables (300)
        return 250;
    }

    @PreDestroy
    void cleanup() {
        if (etcdClient != null) {
            try {
                etcdClient.close();
                logger.info("etcd client closed");
            } catch (Exception e) {
                logger.warn("Error closing etcd client: " + e.getMessage());
            }
        }
    }
}

