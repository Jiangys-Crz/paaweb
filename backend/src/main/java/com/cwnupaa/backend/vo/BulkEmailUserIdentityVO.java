package com.cwnupaa.backend.vo;

import lombok.Data;

/** Only the profile fields required to resolve a mail recipient by university and student number. */
@Data
public class BulkEmailUserIdentityVO {
    private Long userId;
    private String userAccount;
    private String userName;
    private String realName;
    private String numberId;
    private String email;
    private String universityName;
}
