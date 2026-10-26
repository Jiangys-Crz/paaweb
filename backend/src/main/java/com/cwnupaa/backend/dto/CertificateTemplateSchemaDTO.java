package com.cwnupaa.backend.dto;

import java.util.ArrayList;
import java.util.List;

public class CertificateTemplateSchemaDTO {
    private Integer version = 1;
    private List<CertificateTextBlockDTO> blocks = new ArrayList<>();
    private CertificateQrCodeBlockDTO qrCode;

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public List<CertificateTextBlockDTO> getBlocks() { return blocks; }
    public void setBlocks(List<CertificateTextBlockDTO> blocks) { this.blocks = blocks; }
    public CertificateQrCodeBlockDTO getQrCode() { return qrCode; }
    public void setQrCode(CertificateQrCodeBlockDTO qrCode) { this.qrCode = qrCode; }
}
