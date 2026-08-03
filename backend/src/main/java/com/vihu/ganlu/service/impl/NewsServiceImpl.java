package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.NewsEntity;
import com.vihu.ganlu.mappers.NewsMapper;
import com.vihu.ganlu.service.NewService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NewsServiceImpl implements NewService {
    private final NewsMapper newsMapper;
    private final PublicImageLifecycleService imageLifecycleService;

    public NewsServiceImpl(
            NewsMapper newsMapper,
            PublicImageLifecycleService imageLifecycleService) {
        this.newsMapper = newsMapper;
        this.imageLifecycleService = imageLifecycleService;
    }
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
    @Transactional
    public int addNews(NewsEntity news) {
        requireUploadOwner(news);
        news.setImageUrl(imageLifecycleService.promote(
                news.getImageUploadUserId(), news.getImageUploadToken()));
        int inserted = newsMapper.insert(news);
        if (inserted != 1) throw new IllegalStateException("保存新闻失败");
        return inserted;
    }

    @Override
    @Transactional
    public int updateNews(NewsEntity news) {
        NewsEntity existing = news == null ? null : newsMapper.findById(news.getId());
        if (existing == null) return 0;
        String oldPath = existing.getImageUrl();
        boolean replacingImage = hasUploadToken(news);
        if (replacingImage) {
            requireUploadOwner(news);
            imageLifecycleService.deletePublicImageAfterCommit(oldPath);
            news.setImageUrl(imageLifecycleService.promote(
                    news.getImageUploadUserId(), news.getImageUploadToken()));
        } else {
            news.setImageUrl(oldPath);
        }
        int updated = newsMapper.update(news);
        if (updated != 1 && replacingImage) throw new IllegalStateException("更新新闻失败");
        return updated;
    }

    @Override
    @Transactional
    public int deleteNews(int id) {
        NewsEntity existing = newsMapper.findById(id);
        int deleted = newsMapper.delete(id);
        if (deleted == 1 && existing != null) {
            imageLifecycleService.deletePublicImageAfterCommit(existing.getImageUrl());
        }
        return deleted;
    }

    private boolean hasUploadToken(NewsEntity news) {
        return news != null && news.getImageUploadToken() != null
                && !news.getImageUploadToken().trim().isEmpty();
    }

    private void requireUploadOwner(NewsEntity news) {
        if (!hasUploadToken(news) || news.getImageUploadUserId() == null) {
            throw new IllegalArgumentException("请先上传并暂存新闻封面");
        }
    }
}
