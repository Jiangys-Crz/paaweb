package com.cwnupaa.backend.vo;

import java.util.ArrayList;
import java.util.List;

public class CertificateImportPreviewVO {
    private boolean valid;
    private Integer certificateCount;
    private Integer recipientCount;
    private List<CertificateImportRowVO> rows = new ArrayList<>();
    private List<CertificateImportErrorVO> errors = new ArrayList<>();

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public Integer getCertificateCount() { return certificateCount; }
    public void setCertificateCount(Integer certificateCount) { this.certificateCount = certificateCount; }
    public Integer getRecipientCount() { return recipientCount; }
    public void setRecipientCount(Integer recipientCount) { this.recipientCount = recipientCount; }
    public List<CertificateImportRowVO> getRows() { return rows; }
    public void setRows(List<CertificateImportRowVO> rows) { this.rows = rows; }
    public List<CertificateImportErrorVO> getErrors() { return errors; }
    public void setErrors(List<CertificateImportErrorVO> errors) { this.errors = errors; }
}
