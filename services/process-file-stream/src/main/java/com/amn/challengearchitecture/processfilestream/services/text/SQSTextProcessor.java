package com.amn.challengearchitecture.processfilestream.services.text;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amn.challengearchitecture.processfilestream.services.AbstractSQSProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode;
import org.apache.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification;
import org.apache.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Stream;

@Profile("extract-text")
@Component
@Slf4j
public class SQSTextProcessor extends AbstractSQSProcessor {
    private static final String FILE_EXT = ".txt";
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    /**
     * @param s3ObjectInputStream
     * @param fileKey
     * @param bucketName
     */
    protected void process(S3ObjectInputStream s3ObjectInputStream,
                           String fileKey, String bucketName) {
        // Extract Text
        PDDocument document = null;
        Writer output = null;
        try {
            document = PDDocument.load(s3ObjectInputStream);

            final Path tempFilePath = Paths.get(TMP_DIR, fileKey + FILE_EXT);

            output = new OutputStreamWriter(new FileOutputStream(tempFilePath.toFile()),
                    StandardCharsets.UTF_8);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setShouldSeparateByBeads(true);
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), 10));
            stripper.writeText(document, output);

            // ... also for any embedded PDFs:
            PDDocumentCatalog catalog = document.getDocumentCatalog();
            PDDocumentNameDictionary names = catalog.getNames();
            if (names != null) {
                PDEmbeddedFilesNameTreeNode embeddedFiles = names.getEmbeddedFiles();
                if (embeddedFiles != null) {
                    Map<String, PDComplexFileSpecification> embeddedFileNames = embeddedFiles.getNames();
                    if (embeddedFileNames != null) {
                        for (Map.Entry<String, PDComplexFileSpecification> ent : embeddedFileNames.entrySet()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Processing embedded file {} : ", ent.getKey());
                            }
                            PDComplexFileSpecification spec = ent.getValue();
                            PDEmbeddedFile file = spec.getEmbeddedFile();
                            if (file != null && "application/pdf".equals(file.getSubtype())) {
                                if (log.isDebugEnabled()) {
                                    log.debug("  is PDF (size={})", file.getSize());
                                }
                                try (InputStream fis = file.createInputStream();
                                     PDDocument subDoc = PDDocument.load(fis)) {
                                    stripper.writeText(subDoc, output);
                                }
                            }
                        }
                    }
                }
            }

            if (log.isDebugEnabled()) {
                log.debug("##### Extracted Text #####");
                log.debug("Size: {}", Files.size(tempFilePath));
                try (Stream<String> stream = Files.lines(tempFilePath)) {
                    StringBuilder data = new StringBuilder();
                    stream.forEach(data::append);
                    log.debug(data.toString());
                } catch (IOException e) {
                    log.error("Error reading text file", e);
                }

                log.debug("##### Extracted Text Finish #####");
            }

            ObjectMetadata metadata = new ObjectMetadata();
            InputStream tempFileStream = new FileInputStream(tempFilePath.toFile());
            byte[] contentBytes = IOUtils.toByteArray(tempFileStream);
            metadata.setContentLength(contentBytes.length);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
                    "extracted-text/" + fileKey,
                    tempFileStream, metadata);

            s3Client.putObject(putObjectRequest);
            log.info("Uploaded extracted content of {} to S3 with key extracted-text/{}", fileKey, fileKey);

            // Delete file locally
            try {
                Files.delete(tempFilePath);
            } catch (IOException e) {
                log.error("Error deleting the file", e);
            }
        } catch (IOException e) {
            log.error("Error reading or writing", e);
        } finally {
            IOUtils.closeQuietly(output);
            IOUtils.closeQuietly(document);
        }
    }

    @Override
    protected String getKey() {
        return "TEXT";
    }
}
