package com.cwnupaa.backend.vo;

import java.util.List;

public class BulkEmailParseResultVO {
    private List<BulkEmailRecipientVO> recipients;
    private List<BulkEmailRecipientVO> invalidRecipients;
    private List<String> keywords;

    public List<BulkEmailRecipientVO> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<BulkEmailRecipientVO> recipients) {
        this.recipients = recipients;
    }

    public List<BulkEmailRecipientVO> getInvalidRecipients() {
        return invalidRecipients;
    }

    public void setInvalidRecipients(List<BulkEmailRecipientVO> invalidRecipients) {
        this.invalidRecipients = invalidRecipients;
    }

    public List<String> getKeywords() {
        return keywords;
    }

    public void setKeywords(List<String> keywords) {
        this.keywords = keywords;
    }
}
