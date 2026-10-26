package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("oj_platform")
public class OjPlatform {
    @TableId(type = IdType.AUTO)
    private Long ojId;
    private String ojName;
    private String ojUrl;

    public Long getOjId() {
        return ojId;
    }

    public void setOjId(Long ojId) {
        this.ojId = ojId;
    }

    public String getOjName() {
        return ojName;
    }

    public void setOjName(String ojName) {
        this.ojName = ojName;
    }

    public String getOjUrl() {
        return ojUrl;
    }

    public void setOjUrl(String ojUrl) {
        this.ojUrl = ojUrl;
    }
}