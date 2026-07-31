package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    // ===================== 遗留旧接口（过渡使用，Controller逐步迁移，请勿删除） =====================
    // 新增留言【旧】
    int insertMessage(MessageEntity message);

    // 获取留言列表（分页）【旧分页接口，后续替换为selectPage】
    List<MessageEntity> selectMessages(@Param("offset") int offset, @Param("pageSize") int pageSize);

    // 获取留言总数【旧统计方法，后续替换为countByStatus】
    int countMessages();

    // 根据ID获取留言【旧方法，逐步替换为 selectById】
    MessageEntity selectMessageById(Integer id);

    // 物理删除留言【旧删除方法，逐步替换 logicDeleteById】
    int deleteMessage(Integer id);

    // 获取用户留言
    List<MessageEntity> selectMessagesByUserId(Integer userId);

    // ===================== 【方案A 新标准接口，Service直接调用这批】 =====================
    /**
     * 分页查询指定状态留言
     * @param status 状态 1正常 0删除
     * @param offset 偏移量
     * @param pageSize 每页条数
     */
    List<MessageEntity> selectPage(@Param("status") Integer status,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    /**
     * 统计指定状态留言总数
     */
    int countByStatus(@Param("status") Integer status);

    /**
     * 根据主键ID查询单条留言（新标准）
     */
    MessageEntity selectById(@Param("id") Integer id);

    /**
     * 逻辑删除，更新status=0（新标准，替代deleteMessage物理删除）
     */
    int logicDeleteById(@Param("id") Integer id);
}