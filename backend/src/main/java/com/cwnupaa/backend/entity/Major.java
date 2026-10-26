package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("major")
public class Major {
    @TableId(type = IdType.AUTO)
    private Long majorId;
    private Long schoolId;
    private String majorName;
    private Integer schoolingYears;

    public Long getMajorId() {
        return majorId;
    }

    public void setMajorId(Long majorId) {
        this.majorId = majorId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public String getMajorName() {
        return majorName;
    }

    public void setMajorName(String majorName) {
        this.majorName = majorName;
    }

    public Integer getSchoolingYears() {
        return schoolingYears;
    }

    public void setSchoolingYears(Integer schoolingYears) {
        this.schoolingYears = schoolingYears;
    }
}