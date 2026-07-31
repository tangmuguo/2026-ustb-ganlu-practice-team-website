package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ReplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplyMapper {
    // =====================【原有旧方法，保留过渡】=====================
    // 新增回复（旧）
    int insertReply(ReplyEntity reply);

    // 获取留言的所有回复（旧单条查询，逐步淘汰，避免N+1）
    List<ReplyEntity> selectRepliesByMessageId(Integer messageId);

    // 根据ID获取回复（旧）
    ReplyEntity selectReplyById(Integer id);

    // 逻辑删除回复（旧）
    int deleteReply(Integer id);

    // 获取用户回复
    List<ReplyEntity> selectRepliesByUserId(Integer userId);

    // =====================【任务新增标准接口】=====================
    // 【核心优化】批量查询回复：一次查询多个messageId下所有回复，解决N+1
    List<ReplyEntity> selectByMessageIds(@Param("messageIds") List<Integer> messageIds,
                                         @Param("status") Integer status);

    // 按ID查询单条回复（删除前权限校验，可以查出已删除数据）
    ReplyEntity selectById(@Param("id") Integer id);

    // 逻辑删除回复（新接口，统一命名规范）
    int logicDeleteById(@Param("id") Integer id);

    // 新增回复（新标准接口）
    int insert(ReplyEntity reply);
}