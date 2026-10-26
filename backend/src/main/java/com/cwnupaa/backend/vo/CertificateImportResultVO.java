package com.cwnupaa.backend.vo;

public class CertificateImportResultVO {
    private Integer certificateCount;
    private Integer recipientCount;
    private String message;

    public Integer getCertificateCount() { return certificateCount; }
    public void setCertificateCount(Integer certificateCount) { this.certificateCount = certificateCount; }
    public Integer getRecipientCount() { return recipientCount; }
    public void setRecipientCount(Integer recipientCount) { this.recipientCount = recipientCount; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
