package com.cwnupaa.backend.vo;

import com.cwnupaa.backend.entity.FileSystem;
import com.cwnupaa.backend.entity.Notice;

import java.util.List;

public class NoticeVO extends Notice {
    private String publisherName;
    private String publisherAvatar;
    private List<FileSystem> attachments;

    public String getPublisherName() {
        return publisherName;
    }

    public void setPublisherName(String publisherName) {
        this.publisherName = publisherName;
    }

    public String getPublisherAvatar() {
        return publisherAvatar;
    }

    public void setPublisherAvatar(String publisherAvatar) {
        this.publisherAvatar = publisherAvatar;
    }

    public List<FileSystem> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<FileSystem> attachments) {
        this.attachments = attachments;
    }
}