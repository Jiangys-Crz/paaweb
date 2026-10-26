package com.cwnupaa.backend.dto;

public class CertificateTextBlockDTO {
    private String id;
    private String content;
    private Double x;
    private Double y;
    private Double width;
    private Double fontSize;
    private Double minFontSize;
    private String fontFamily;
    private String alignment;
    private String color;
    private Boolean autoFit;
    private Boolean bold;
    private Boolean underline;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Double getX() { return x; }
    public void setX(Double x) { this.x = x; }
    public Double getY() { return y; }
    public void setY(Double y) { this.y = y; }
    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }
    public Double getFontSize() { return fontSize; }
    public void setFontSize(Double fontSize) { this.fontSize = fontSize; }
    public Double getMinFontSize() { return minFontSize; }
    public void setMinFontSize(Double minFontSize) { this.minFontSize = minFontSize; }
    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }
    public String getAlignment() { return alignment; }
    public void setAlignment(String alignment) { this.alignment = alignment; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Boolean getAutoFit() { return autoFit; }
    public void setAutoFit(Boolean autoFit) { this.autoFit = autoFit; }
    public Boolean getBold() { return bold; }
    public void setBold(Boolean bold) { this.bold = bold; }
    public Boolean getUnderline() { return underline; }
    public void setUnderline(Boolean underline) { this.underline = underline; }
}
