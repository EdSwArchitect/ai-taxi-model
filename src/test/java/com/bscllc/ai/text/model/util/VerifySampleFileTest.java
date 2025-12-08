package com.bscllc.ai.text.model.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.bscllc.ai.text.model.input.YellowReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verify the sample file was created correctly.
 */
@DisplayName("Verify Sample File")
class VerifySampleFileTest {

    @Test
    @DisplayName("Should verify sample file has 600000 records")
    void testVerifySampleFile() throws Exception {
        Path sampleFile = Paths.get("sample_yellow_tripdata_2025_01.parquet");
        
        if (!Files.exists(sampleFile)) {
            System.out.println("Sample file not found: " + sampleFile);
            return;
        }

        YellowReader reader = new YellowReader();
        List records = reader.readAll(sampleFile);
        
        System.out.println("Sample file contains: " + records.size() + " records");
        System.out.println("File size: " + Files.size(sampleFile) + " bytes");
        
        assertEquals(600000, records.size(), "Sample file should contain exactly 600000 records");
        assertTrue(Files.size(sampleFile) > 0, "Sample file should not be empty");
    }
}

