package com.cwnupaa.backend.vo;

import java.util.LinkedHashMap;
import java.util.Map;

public class BulkEmailRecipientVO {
    private Integer rowNumber;
    private Long userId;
    private String name;
    private String email;
    private String numberId;
    private String error;
    private Map<String, String> fields = new LinkedHashMap<>();

    public Integer getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(Integer rowNumber) {
        this.rowNumber = rowNumber;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNumberId() {
        return numberId;
    }

    public void setNumberId(String numberId) {
        this.numberId = numberId;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Map<String, String> getFields() {
        return fields;
    }

    public void setFields(Map<String, String> fields) {
        this.fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
    }
}
