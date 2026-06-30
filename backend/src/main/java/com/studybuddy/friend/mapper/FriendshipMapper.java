package com.studybuddy.friend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.studybuddy.friend.entity.Friendship;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FriendshipMapper extends BaseMapper<Friendship> {
}
