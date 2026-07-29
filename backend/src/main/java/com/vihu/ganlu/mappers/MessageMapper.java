package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

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

    // 逻辑删除留言（管理员）
    int deleteMessage(Integer id);

    // 获取用户留言
    List<MessageEntity> selectMessagesByUserId(Integer userId);
}
