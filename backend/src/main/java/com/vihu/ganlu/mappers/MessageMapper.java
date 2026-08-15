package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;
@Mapper
public interface MessageMapper {
    // 新增留言
    int insertMessage(MessageEntity message);

    // 获取留言列表（分页）
    List<MessageEntity> selectMessages(@Param("offset") int offset, @Param("pageSize") int pageSize);

    // 获取留言总数
    int countMessages();

    // 根据ID获取留言
    MessageEntity selectMessageById(Integer id);
    MessageEntity selectMessageForModeration(@Param("id") Integer id);
    List<MessageEntity> selectPendingMessages(@Param("offset") int offset, @Param("pageSize") int pageSize);
    int countPendingMessages();
    int updateContentStatusByAdmin(@Param("id") Integer id,
                                   @Param("actorUserId") Integer actorUserId,
                                   @Param("newStatus") String newStatus,
                                   @Param("reasonCode") String reasonCode,
                                   @Param("note") String note);
    int removeMessageForActor(@Param("id") Integer id,
                              @Param("actorUserId") Integer actorUserId,
                              @Param("reasonCode") String reasonCode);

    int countRecentDuplicate(@Param("userId") Integer userId,
                             @Param("content") String content,
                             @Param("since") Date since);
}
