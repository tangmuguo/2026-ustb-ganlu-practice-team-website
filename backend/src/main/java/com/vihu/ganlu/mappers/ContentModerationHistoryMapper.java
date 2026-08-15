package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.ContentModerationHistoryEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContentModerationHistoryMapper {
    int insert(ContentModerationHistoryEntity history);
}
