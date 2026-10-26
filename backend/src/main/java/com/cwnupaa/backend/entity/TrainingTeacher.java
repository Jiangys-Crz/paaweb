package com.cwnupaa.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("training_teacher")
public class TrainingTeacher {
    @TableId(type = IdType.AUTO)
    private Long trainingTeacherId;
    private Long trainId;
    private Long teacherId;
    private Integer sortOrder;

    public Long getTrainingTeacherId() {
        return trainingTeacherId;
    }

    public void setTrainingTeacherId(Long trainingTeacherId) {
        this.trainingTeacherId = trainingTeacherId;
    }

    public Long getTrainId() {
        return trainId;
    }

    public void setTrainId(Long trainId) {
        this.trainId = trainId;
    }

    public Long getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(Long teacherId) {
        this.teacherId = teacherId;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
