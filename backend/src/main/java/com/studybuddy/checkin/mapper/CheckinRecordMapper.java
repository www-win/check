package com.studybuddy.checkin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.checkin.entity.CheckinRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface CheckinRecordMapper extends BaseMapper<CheckinRecord> {

    /** 两人在同一天都打过卡的天数。 */
    @Select("SELECT COUNT(*) FROM (" +
            "  SELECT checkin_date FROM checkin_record " +
            "  WHERE user_id IN (#{a}, #{b}) " +
            "  GROUP BY checkin_date HAVING COUNT(DISTINCT user_id) = 2" +
            ") t")
    int countCommonDays(@Param("a") Long a, @Param("b") Long b);
}
