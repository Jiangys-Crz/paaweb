package com.cwnupaa.backend.vo;

public class CertificateImportErrorVO {
    private Integer rowNumber;
    private String columnName;
    private String message;

    public CertificateImportErrorVO() { }

    public CertificateImportErrorVO(Integer rowNumber, String columnName, String message) {
        this.rowNumber = rowNumber;
        this.columnName = columnName;
        this.message = message;
    }

    public Integer getRowNumber() { return rowNumber; }
    public void setRowNumber(Integer rowNumber) { this.rowNumber = rowNumber; }
    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
