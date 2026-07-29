package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.NewsEntity;

import java.util.List;

public interface NewService {
    // 获取新闻列表（带分页）
    List<NewsEntity> getNewsList(int limit);

    List<NewsEntity> findAll();

    // 获取单个新闻详情
    NewsEntity getNewsById(int id);

    // 添加新闻
    int addNews(NewsEntity news);

    // 修改新闻
    int updateNews(NewsEntity news);

    // 删除新闻
    int deleteNews(int id);
}
