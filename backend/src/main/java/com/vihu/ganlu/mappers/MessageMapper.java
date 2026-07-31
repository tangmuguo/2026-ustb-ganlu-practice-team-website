package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.MessageEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface MessageMapper {
    List<MessageEntity> selectPage(@Param("status") int status,
                                   @Param("offset") int offset,
                                   @Param("limit") int limit);

    int countByStatus(@Param("status") int status);

    MessageEntity selectById(@Param("id") Integer id);

    void insert(MessageEntity entity);

    void logicDeleteById(@Param("id") Integer id);
}