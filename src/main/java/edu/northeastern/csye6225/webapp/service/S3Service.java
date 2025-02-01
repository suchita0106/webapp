package edu.northeastern.csye6225.webapp.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.timgroup.statsd.StatsDClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Service
public class S3Service {

    @Autowired
    private AmazonS3 amazonS3;

    @Autowired
    private StatsDClient statsDClient;

    @Value("${aws.s3.bucket}")
    private String bucketName;

    private static final Logger logger = LoggerFactory.getLogger(S3Service.class);

    public Map<String, Object> uploadFile(MultipartFile multipartFile) throws IOException {
        statsDClient.incrementCounter("api.s3.uploadFile");
        long start = System.currentTimeMillis();

        File file = convertMultiPartFileToFile(multipartFile);
        String fileName = System.currentTimeMillis() + "_" + multipartFile.getOriginalFilename();
        String fileUrl = null;
        ObjectMetadata metadata = null;

        try {
            amazonS3.putObject(new PutObjectRequest(bucketName, fileName, file));
            fileUrl = amazonS3.getUrl(bucketName, fileName).toString();
            metadata = amazonS3.getObjectMetadata(bucketName, fileName);

        } catch (Exception e) {
            statsDClient.incrementCounter("api.s3.uploadFile.error");
            throw e;
        } finally {
            file.delete();
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.s3.uploadFile.time.milliseconds", end - start);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("fileUrl", fileUrl);
        if (metadata != null) {
            result.put("contentType", metadata.getContentType());
            result.put("size", metadata.getContentLength());
            result.put("lastModified", metadata.getLastModified());
        }

        return result;
    }

    public void deleteFile(String fileUrl) {
        statsDClient.incrementCounter("api.s3.deleteFile");
        long start = System.currentTimeMillis();

        String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        try {
            amazonS3.deleteObject(new DeleteObjectRequest(bucketName, fileName));
        } catch (Exception e) {
            statsDClient.incrementCounter("api.s3.deleteFile.error");
            throw e;
        } finally {
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.s3.deleteFile.time.milliseconds", end - start);
        }
    }

    private File convertMultiPartFileToFile(MultipartFile file) throws IOException {
        statsDClient.incrementCounter("api.s3.convertFile");
        long start = System.currentTimeMillis();

        // Create a temp file in the /tmp directory
        Path tempFilePath = Files.createTempFile("upload-", "-" + file.getOriginalFilename());
        File convertedFile = tempFilePath.toFile();

        try (FileOutputStream fos = new FileOutputStream(convertedFile)) {
            fos.write(file.getBytes());
            logger.info("Temporary file created at: {}", convertedFile.getAbsolutePath());
        } catch (IOException e) {
            statsDClient.incrementCounter("api.s3.convertFile.error");
            logger.error("Error creating temporary file for upload: {}", e.getMessage(), e);
            throw e;
        } finally {
            long end = System.currentTimeMillis();
            statsDClient.recordExecutionTime("api.s3.convertFile.time.milliseconds", end - start);
        }

        return convertedFile;
    }
}