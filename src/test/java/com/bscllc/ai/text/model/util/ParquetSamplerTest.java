package com.bscllc.ai.text.model.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test for ParquetSampler utility.
 */
@DisplayName("ParquetSampler Tests")
class ParquetSamplerTest {

    @Test
    @DisplayName("Should create sample file with 600000 records")
    void testCreateSampleFile() throws Exception {
        // Find source file
        Path[] possiblePaths = {
            Paths.get("yellow_tripdata_2025_01.parquet"),
            Paths.get("data/yellow_tripdata_2025_01.parquet"),
            Paths.get("../yellow_tripdata_2025_01.parquet"),
            Paths.get("src/test/resources/yellow_tripdata_2025_01.parquet")
        };

        Path sourceFile = null;
        for (Path path : possiblePaths) {
            if (Files.exists(path) && Files.isRegularFile(path)) {
                sourceFile = path;
                break;
            }
        }

        if (sourceFile == null) {
            System.out.println("Skipping test: yellow_tripdata_2025_01.parquet not found");
            return;
        }

        // Create sample file
        Path targetFile = Paths.get("sample_yellow_tripdata_2025_01.parquet");
        
        System.out.println("Creating sample file: " + targetFile);
        System.out.println("Source file: " + sourceFile);
        
        ParquetSampler.sampleParquetFile(sourceFile, targetFile, 600000);
        
        // Verify file was created
        assert Files.exists(targetFile) : "Sample file should be created";
        assert Files.size(targetFile) > 0 : "Sample file should not be empty";
        
        System.out.println("Successfully created sample file: " + targetFile);
        System.out.println("File size: " + Files.size(targetFile) + " bytes");
    }
}

