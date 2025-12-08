package com.bscllc.ai.text.model.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path as HadoopPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.avro.AvroParquetWriter;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.hadoop.ParquetWriter;
import org.apache.parquet.hadoop.util.HadoopInputFile;
import org.apache.parquet.hadoop.util.HadoopOutputFile;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.OutputFile;

/**
 * Utility class for sampling records from Parquet files.
 */
public class ParquetSampler {
    private static final Logger logger = LogManager.getLogger(ParquetSampler.class);

    /**
     * Samples a specified number of records from a source Parquet file and writes them to a new file.
     * 
     * @param sourceFile Path to the source Parquet file
     * @param targetFile Path to the target Parquet file to create
     * @param numRecords Number of records to sample
     * @throws IOException if there's an error reading or writing files
     */
    public static void sampleParquetFile(Path sourceFile, Path targetFile, int numRecords) throws IOException {
        if (!Files.exists(sourceFile)) {
            throw new IOException("Source file does not exist: " + sourceFile);
        }

        logger.info("Sampling {} records from {} to {}", numRecords, sourceFile, targetFile);

        // Configure Hadoop to use local filesystem
        Configuration conf = new Configuration();
        conf.set("fs.defaultFS", "file:///");
        conf.setBoolean("fs.file.impl.disable.cache", true);

        HadoopPath sourceHadoopPath = new HadoopPath(sourceFile.toUri());
        InputFile inputFile = HadoopInputFile.fromPath(sourceHadoopPath, conf);

        // Read first record to get schema
        Schema schema;
        try (ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            GenericRecord firstRecord = reader.read();
            if (firstRecord == null) {
                throw new IOException("Source file is empty: " + sourceFile);
            }
            schema = firstRecord.getSchema();
            logger.info("Extracted schema from source file: {}", schema.getName());
        }

        // Count total records first (for sampling strategy)
        long totalRecords = countRecords(inputFile);
        logger.info("Source file contains {} total records", totalRecords);

        if (totalRecords < numRecords) {
            logger.warn("Source file has only {} records, but {} requested. Using all records.", 
                totalRecords, numRecords);
            numRecords = (int) totalRecords;
        }

        // Create output file
        HadoopPath targetHadoopPath = new HadoopPath(targetFile.toUri());
        OutputFile outputFile = HadoopOutputFile.fromPath(targetHadoopPath, conf);

        // Sample and write records
        try (
            ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build();
            ParquetWriter<GenericRecord> writer = AvroParquetWriter.<GenericRecord>builder(outputFile)
                .withSchema(schema)
                .withConf(conf)
                .build()
        ) {
            Random random = new Random(42); // Fixed seed for reproducibility
            long recordsWritten = 0;
            long recordsRead = 0;
            GenericRecord record;

            // Use reservoir sampling for uniform random sampling
            GenericRecord[] reservoir = new GenericRecord[numRecords];
            int reservoirIndex = 0;

            // Fill reservoir with first numRecords
            while (reservoirIndex < numRecords && (record = reader.read()) != null) {
                reservoir[reservoirIndex] = record;
                reservoirIndex++;
                recordsRead++;
            }

            // Continue reading and replace reservoir entries with decreasing probability
            while ((record = reader.read()) != null) {
                recordsRead++;
                int randomIndex = random.nextInt((int) recordsRead);
                if (randomIndex < numRecords) {
                    reservoir[randomIndex] = record;
                }

                // Progress logging
                if (recordsRead % 100000 == 0) {
                    logger.info("Read {} records, sampled {} so far", recordsRead, numRecords);
                }
            }

            // Write all sampled records
            logger.info("Writing {} sampled records to {}", numRecords, targetFile);
            for (GenericRecord sampledRecord : reservoir) {
                if (sampledRecord != null) {
                    writer.write(sampledRecord);
                    recordsWritten++;
                }
            }

            logger.info("Successfully created sample file with {} records", recordsWritten);
        }
    }

    /**
     * Counts the total number of records in a Parquet file.
     */
    private static long countRecords(InputFile inputFile) throws IOException {
        long count = 0;
        try (ParquetReader<GenericRecord> reader = 
                AvroParquetReader.<GenericRecord>builder(inputFile).build()) {
            while (reader.read() != null) {
                count++;
                if (count % 500000 == 0) {
                    logger.debug("Counted {} records so far", count);
                }
            }
        }
        return count;
    }

    /**
     * Main method for command-line usage.
     */
    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("Usage: ParquetSampler <sourceFile> <targetFile> <numRecords>");
            System.exit(1);
        }

        try {
            Path sourceFile = Paths.get(args[0]);
            Path targetFile = Paths.get(args[1]);
            int numRecords = Integer.parseInt(args[2]);

            sampleParquetFile(sourceFile, targetFile, numRecords);
            System.out.println("Successfully created sample file: " + targetFile);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}

