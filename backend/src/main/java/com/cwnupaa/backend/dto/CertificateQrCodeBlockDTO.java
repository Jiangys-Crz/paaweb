package com.cwnupaa.backend.dto;

public class CertificateQrCodeBlockDTO {
    private Boolean enabled = false;
    private Double x;
    private Double y;
    private Double size;

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public Double getSize() { return size; }
    public void setSize(Double size) { this.size = size; }
}
