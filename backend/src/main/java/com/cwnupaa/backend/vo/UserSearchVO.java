package com.cwnupaa.backend.vo;

import lombok.Data;

/** 公开搜索字段白名单，不包含任何登录标识或身份资料。 */
@Data
public class UserSearchVO {
    private Long userId;
    private String userName;
    private Long avatarFileId;
    private String avatarUrl;
}
