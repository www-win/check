package com.studybuddy.couple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.couple.entity.Couple;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoupleMapper extends BaseMapper<Couple> {
}
