package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("external_app")
public class ExternalApp {
    @TableId(type = IdType.AUTO)
    private Long appId;
    private String appName;
    private String appKey;
    private Integer status;
    private Boolean externalLoginEnabled;
    private Boolean externalBoardEnabled;
    private LocalDateTime createTime;

    public Boolean getExternalLoginEnabled() {
        return externalLoginEnabled;
    }

    public void setExternalLoginEnabled(Boolean externalLoginEnabled) {
        this.externalLoginEnabled = externalLoginEnabled;
    }

    public Boolean getExternalBoardEnabled() {
        return externalBoardEnabled;
    }

    public void setExternalBoardEnabled(Boolean externalBoardEnabled) {
        this.externalBoardEnabled = externalBoardEnabled;
    }

    public Long getAppId() {
        return appId;
    }

    public void setAppId(Long appId) {
        this.appId = appId;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
