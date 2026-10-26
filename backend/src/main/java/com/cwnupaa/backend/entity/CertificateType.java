package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDate;

@TableName("certificate_type")
public class CertificateType {
    @TableId(type = IdType.AUTO)
    private Long certificateTypeId;

    private String typeName;
    private Long typeId;
    private Long urlId;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate time;

    @TableField("term")
    private Long term;

    @TableField("level_scope")
    private String levelScope;

    private Long updateUserId;

    private Integer fontSize;
    
    // 0: Individual, 1: Team
    @TableField("is_team")
    private Integer isTeam;

    @TableField("template_schema")
    private String templateSchema;

    @TableField("rating_enabled")
    private Boolean ratingEnabled;

    @TableField(exist = false)
    private String templateFileName;

    @TableField(exist = false)
    private String templateFileUrl;

    public Long getCertificateTypeId() {
        return certificateTypeId;
    }

    public void setCertificateTypeId(Long certificateTypeId) {
        this.certificateTypeId = certificateTypeId;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public Long getTypeId() {
        return typeId;
    }

    public void setTypeId(Long typeId) {
        this.typeId = typeId;
    }

    public Long getUrlId() {
        return urlId;
    }

    public void setUrlId(Long urlId) {
        this.urlId = urlId;
    }

    public LocalDate getTime() {
        return time;
    }

    public void setTime(LocalDate time) {
        this.time = time;
    }

    public Long getTerm() {
        return term;
    }

    public void setTerm(Long term) {
        this.term = term;
    }

    public String getLevelScope() {
        return levelScope;
    }

    public void setLevelScope(String levelScope) {
        this.levelScope = levelScope;
    }

    public Long getUpdateUserId() {
        return updateUserId;
    }

    public void setUpdateUserId(Long updateUserId) {
        this.updateUserId = updateUserId;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public Integer getIsTeam() {
        return isTeam;
    }

    public void setIsTeam(Integer isTeam) {
        this.isTeam = isTeam;
    }

    public String getTemplateSchema() {
        return templateSchema;
    }

    public void setTemplateSchema(String templateSchema) {
        this.templateSchema = templateSchema;
    }

    public Boolean getRatingEnabled() {
        return ratingEnabled;
    }

    public void setRatingEnabled(Boolean ratingEnabled) {
        this.ratingEnabled = ratingEnabled;
    }

    public String getTemplateFileName() {
        return templateFileName;
    }

    public void setTemplateFileName(String templateFileName) {
        this.templateFileName = templateFileName;
    }

    public String getTemplateFileUrl() {
        return templateFileUrl;
    }

    public void setTemplateFileUrl(String templateFileUrl) {
        this.templateFileUrl = templateFileUrl;
    }
}
