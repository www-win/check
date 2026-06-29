package com.studybuddy.goal.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("study_goal")
public class StudyGoal {
    /** 主键即 user_id（业务写入，非自增）。 */
    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;
    private String content;
    private LocalDate targetDate;
    private LocalDateTime updatedAt;
}
