package com.cwnupaa.backend.vo;

import java.util.List;

public class BulkEmailSendResultVO {
    private Integer successCount;
    private Integer failedCount;
    private List<BulkEmailRecipientVO> failedRecipients;

    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    public List<BulkEmailRecipientVO> getFailedRecipients() {
        return failedRecipients;
    }

    public void setFailedRecipients(List<BulkEmailRecipientVO> failedRecipients) {
        this.failedRecipients = failedRecipients;
    }
}
