// ImageDTO.java
package edu.northeastern.csye6225.webapp.dto;

import java.util.Date;
import java.util.UUID;

public class ImageDTO {
    private String fileName;
    private UUID id;  // Adjust this to be UUID to match your model's ID type
    private String url;
    private Date uploadDate;
    private UUID userId;

    public ImageDTO(String fileName, UUID id, String url, Date uploadDate, UUID userId) {
        this.fileName = fileName;
        this.id = id;
        this.url = url;
        this.uploadDate = uploadDate;
        this.userId = userId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

}