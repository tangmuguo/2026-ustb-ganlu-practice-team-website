package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ReplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReplyMapper {
    // =====================【遗留旧方法，保留过渡使用，新项目优先调用下方新标准接口】=====================
    // 新增回复（旧）
    int insertReply(ReplyEntity reply);

    // 获取留言的所有回复（旧单条循环查询，会产生N+1，逐步淘汰）
    List<ReplyEntity> selectRepliesByMessageId(Integer messageId);

    // 根据ID获取回复（旧）
    ReplyEntity selectReplyById(Integer id);

    // 逻辑删除回复（旧）
    int deleteReply(Integer id);

    // 获取用户回复
    List<ReplyEntity> selectRepliesByUserId(Integer userId);

    // =====================【任务新标准接口（方案A Service直接调用这批）】=====================
    /**
     * 批量查询一批留言对应的回复（消除N+1核心方法）
     * @param msgIdList 留言id集合
     * @param status 状态：1正常，0删除
     */
    List<ReplyEntity> selectByMessageIds(@Param("msgIdList") List<Integer> msgIdList,
                                         @Param("status") Integer status);

    /**
     * 根据主键ID查询单条回复（权限校验，可以查询到已删除数据）
     */
    ReplyEntity selectById(@Param("id") Integer id);

    /**
     * 新增回复【新标准】
     */
    int insert(ReplyEntity reply);

    /**
     * 逻辑删除回复（更新status=0）【新标准】
     */
    int logicDeleteById(@Param("id") Integer id);
}