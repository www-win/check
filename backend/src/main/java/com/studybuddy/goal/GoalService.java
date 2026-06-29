package com.studybuddy.goal;

import com.studybuddy.goal.dto.GoalReq;
import com.studybuddy.goal.dto.GoalResp;
import com.studybuddy.goal.entity.StudyGoal;
import com.studybuddy.goal.mapper.StudyGoalMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class GoalService {
    private final StudyGoalMapper goalMapper;

    /** 当前目标，未设定返回 null。 */
    public GoalResp get(Long userId) {
        StudyGoal g = goalMapper.selectById(userId);
        if (g == null) {
            return null;
        }
        return new GoalResp(g.getContent(), g.getTargetDate());
    }

    /** 新建或更新当前目标。 */
    public GoalResp upsert(Long userId, GoalReq req) {
        StudyGoal g = goalMapper.selectById(userId);
        boolean isNew = g == null;
        if (isNew) {
            g = new StudyGoal();
            g.setUserId(userId);
        }
        g.setContent(req.getContent());
        g.setTargetDate(req.getTargetDate());
        g.setUpdatedAt(LocalDateTime.now());
        if (isNew) {
            goalMapper.insert(g);
        } else {
            goalMapper.updateById(g);
        }
        return new GoalResp(g.getContent(), g.getTargetDate());
    }

    /** 清除当前目标。 */
    public void clear(Long userId) {
        goalMapper.deleteById(userId);
    }
}
