package com.cwnupaa.backend.dto;

import com.cwnupaa.backend.vo.BulkEmailRecipientVO;

import java.util.List;

public class BulkEmailSendRequestDTO {
    private String subject;
    private String htmlContent;
    private List<BulkEmailRecipientVO> recipients;

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHtmlContent() {
        return htmlContent;
    }

    public void setHtmlContent(String htmlContent) {
        this.htmlContent = htmlContent;
    }

    public List<BulkEmailRecipientVO> getRecipients() {
        return recipients;
    }

    public void setRecipients(List<BulkEmailRecipientVO> recipients) {
        this.recipients = recipients;
    }
}
