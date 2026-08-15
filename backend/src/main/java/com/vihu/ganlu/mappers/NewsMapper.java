package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.NewsEntity;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface NewsMapper {
    // 查询所有新闻（带分页）
    List<NewsEntity> findAllWithLimit(int limit);

    List<NewsEntity> findAll();

    // 根据ID查询新闻
    NewsEntity findById(int id);
    NewsEntity findByIdForUpdate(int id);

    // 新增新闻
    int insert(@Param("news") NewsEntity news, @Param("actorUserId") int actorUserId);

    // 更新新闻
    int update(@Param("news") NewsEntity news, @Param("actorUserId") int actorUserId);

    // 删除新闻
    int delete(@Param("id") int id, @Param("actorUserId") int actorUserId);
}
