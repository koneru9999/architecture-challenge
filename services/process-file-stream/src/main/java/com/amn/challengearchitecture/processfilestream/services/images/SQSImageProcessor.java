package com.amn.challengearchitecture.processfilestream.services.images;

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amn.challengearchitecture.processfilestream.services.AbstractSQSProcessor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

@Profile("extract-image-preview")
@Component
@Slf4j
public class SQSImageProcessor extends AbstractSQSProcessor {

    @Override
    protected void process(S3ObjectInputStream s3ObjectInputStream,
                           String fileKey, String bucketName) {

        try (PDDocument document = PDDocument.load(s3ObjectInputStream)) {
            int maxPages = Math.min(document.getNumberOfPages(), 10);
            PDFRenderer renderer = new PDFRenderer(document);
            int i = 0;
            boolean success = true;
            while (i < maxPages) {
                BufferedImage image = renderer.renderImageWithDPI(i, 96, ImageType.RGB);
                String fileName = fileKey + (i + 1) + ".png";
                success &= ImageIOUtil.writeImage(image, fileName, 96, 1.0f);

                if (success) {
                    ObjectMetadata metadata = new ObjectMetadata();
                    InputStream tempFileStream = new FileInputStream(fileName);
                    byte[] contentBytes = IOUtils.toByteArray(tempFileStream);
                    metadata.setContentLength(contentBytes.length);

                    PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName,
                            "extracted-text/" + fileName,
                            tempFileStream, metadata);

                    s3Client.putObject(putObjectRequest);
                    log.info("Uploaded thumbnail for {} page {} to S3 with key extracted-text/{}", fileKey, i + 1, fileName);

                    // Delete file locally
                    try {
                        Files.delete(Paths.get(fileName));
                    } catch (IOException e) {
                        log.error("Error deleting the file", e);
                    }
                }
                i++;
            }
        } catch (IOException e) {

        }
    }
}
