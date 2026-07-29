package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.NewsEntity;

import java.util.List;

public interface NewsMapper {
    // 查询所有新闻（带分页）
    List<NewsEntity> findAllWithLimit(int limit);

    List<NewsEntity> findAll();

    // 根据ID查询新闻
    NewsEntity findById(int id);

    // 新增新闻
    int insert(NewsEntity news);

    // 更新新闻
    int update(NewsEntity news);

    // 删除新闻
    int delete(int id);
}
