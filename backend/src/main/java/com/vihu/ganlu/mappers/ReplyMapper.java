package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ReplyEntity;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
@Mapper
public interface ReplyMapper {
    // 新增回复
    int insertReply(ReplyEntity reply);

    // 获取留言的所有回复
    List<ReplyEntity> selectRepliesByMessageId(Integer messageId);

    // 根据ID获取回复
    ReplyEntity selectReplyById(Integer id);

    // 逻辑删除回复（管理员）
    int deleteReply(Integer id);

    // 获取用户回复
    List<ReplyEntity> selectRepliesByUserId(Integer userId);
}
