package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MessageMapper {
    // 【原有方法保留】新增留言
    int insertMessage(MessageEntity message);

    // =====================【新增/重构分页相关方法】=====================
    // 分页查询有效留言（status=1）
    List<MessageEntity> selectPage(@Param("status") Integer status,
                                   @Param("offset") int offset,
                                   @Param("pageSize") int pageSize);

    // 分页总数统计
    int countByStatus(@Param("status") Integer status);

    // =====================【重构查询、逻辑删除方法】=====================
    // 根据ID获取留言（校验留言是否存在、是否被删除）
    MessageEntity selectById(@Param("id") Integer id);

    // 逻辑删除留言（更新status=0）
    int logicDeleteById(@Param("id") Integer id);

    // =====================【原有旧方法，标记待淘汰】=====================
    // 获取留言列表（分页）【旧分页接口，后续替换为selectPage，建议保留过渡】
    List<MessageEntity> selectMessages(@Param("offset") int offset, @Param("pageSize") int pageSize);

    // 获取留言总数【旧统计方法，后续替换为countByStatus】
    int countMessages();

    // 根据ID获取留言【旧方法，逐步替换为 selectById】
    MessageEntity selectMessageById(Integer id);

    // 逻辑删除留言（管理员）【旧删除方法，逐步替换 logicDeleteById】
    int deleteMessage(Integer id);

    // 获取用户留言
    List<MessageEntity> selectMessagesByUserId(Integer userId);
}
