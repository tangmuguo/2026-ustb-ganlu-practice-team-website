package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.BannerEntity;
import com.vihu.ganlu.mappers.BannerMapper;
import com.vihu.ganlu.service.BannerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

@Service
public class BannerServiceImpl implements BannerService {
    @Resource
    BannerMapper bannerMapper;

    @Override
    public List<BannerEntity> getAllBanners() {
        return bannerMapper.findAll();
    }

    @Override
    public int getBannerCount() {
        return bannerMapper.count();
    }

    @Override
    @Transactional
    public int addBanner(BannerEntity banner) {
        return bannerMapper.insert(banner);
    }

    @Override
    @Transactional
    public int updateBanner(BannerEntity banner) {
        return bannerMapper.update(banner);
    }

    @Override
    @Transactional
    public int deleteBanner(Integer id) {
        return bannerMapper.delete(id);
    }

    @Override
    @Transactional
    public int updateBannerSort(Integer id, Integer sortOrder) {
        return bannerMapper.updateSort(id, sortOrder);
    }

    @Override
    @Transactional
    public int updateBannerStatus(Integer id, Integer isVisible) {
        return bannerMapper.updateStatus(id, isVisible);
    }

    @Override
    @Transactional
    public int updateBannerLink(Integer id, String linkUrl) {
        return bannerMapper.updateLink(id, linkUrl);
    }

}
