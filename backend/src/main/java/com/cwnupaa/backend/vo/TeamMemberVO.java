package com.cwnupaa.backend.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** Fields required for team collaboration; private identity is limited to self and leader. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TeamMemberVO {
    private Long userId;
    private String userName;
    private String avatarUrl;
    private String realName;
    private String numberId;
}
