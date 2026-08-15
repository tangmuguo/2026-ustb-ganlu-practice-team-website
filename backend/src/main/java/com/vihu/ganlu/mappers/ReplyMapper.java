package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ReplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ReplyMapper {
    // 新增回复
    int insertReply(ReplyEntity reply);

    // 获取留言的所有回复
    List<ReplyEntity> selectRepliesByMessageId(Integer messageId);
    // 获取当前页留言的所有回复
    List<ReplyEntity> selectRepliesByMessageIds(@Param("messageIds") List<Integer> messageIds);

    // 根据ID获取回复
    ReplyEntity selectReplyById(Integer id);
    ReplyEntity selectReplyForModeration(@Param("id") Integer id);
    List<ReplyEntity> selectPendingReplies(@Param("offset") int offset, @Param("pageSize") int pageSize);
    int countPendingReplies();
    int updateContentStatusByAdmin(@Param("id") Integer id,
                                   @Param("actorUserId") Integer actorUserId,
                                   @Param("newStatus") String newStatus,
                                   @Param("reasonCode") String reasonCode,
                                   @Param("note") String note);
    int removeReplyForActor(@Param("id") Integer id,
                            @Param("actorUserId") Integer actorUserId,
                            @Param("reasonCode") String reasonCode);
    /**
     * Returns every non-removed reply attached to a parent that an administrator
     * is about to remove. The service uses this snapshot to preserve one history
     * and one audit event per cascaded reply.
     */
    List<ReplyEntity> selectRepliesForRemoval(@Param("messageId") Integer messageId);

    int countRecentDuplicate(@Param("userId") Integer userId,
                             @Param("messageId") Integer messageId,
                             @Param("content") String content,
                             @Param("since") Date since);
}
