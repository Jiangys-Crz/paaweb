package com.cwnupaa.backend.vo;

import lombok.Data;

/** 公开主页字段白名单：不继承持久化用户实体。 */
@Data
public class PublicUserProfileVO {
    private Long userId;
    private String userName;
    private String introduce;
    private String avatarUrl;
    private String backgroundUrl;
    private Integer rating;
    private Integer rank;
}
