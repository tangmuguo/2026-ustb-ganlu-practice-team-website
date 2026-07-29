package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.NewsEntity;
import com.vihu.ganlu.mappers.NewsMapper;
import com.vihu.ganlu.service.NewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class NewsServiceImpl implements NewService {
    @Resource
    NewsMapper newsMapper;
    @Override
    public List<NewsEntity> getNewsList(int limit) {
        return newsMapper.findAllWithLimit(limit);
    }

    @Override
    public List<NewsEntity> findAll() {
        return newsMapper.findAll();
    }

    @Override
    public NewsEntity getNewsById(int id) {
        return newsMapper.findById(id);
    }

    @Override
    public int addNews(NewsEntity news) {
        return newsMapper.insert(news);
    }

    @Override
    public int updateNews(NewsEntity news) {
        return newsMapper.update(news);
    }

    @Override
    public int deleteNews(int id) {
        return newsMapper.delete(id);
    }
}
