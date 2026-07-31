package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ReplyEntity;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface ReplyMapper {
    // 批量根据messageId列表查询回复，解决N+1
    List<ReplyEntity> selectByMessageIdList(@Param("messageIdList") List<Integer> messageIdList,
                                            @Param("status") Integer status);

    ReplyEntity selectById(@Param("id") Integer id);

    void insert(ReplyEntity entity);

    void logicDeleteById(@Param("id") Integer id);
}