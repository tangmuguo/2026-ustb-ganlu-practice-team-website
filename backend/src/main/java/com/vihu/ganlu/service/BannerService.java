package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.BannerEntity;

import java.util.List;

public interface BannerService {
    List<BannerEntity> getAllBanners();
    int getBannerCount();
    int addBanner(BannerEntity banner);
    int updateBanner(BannerEntity banner);
    int deleteBanner(Integer id);
    int updateBannerSort(Integer id, Integer sortOrder);
    int updateBannerStatus(Integer id, Integer isVisible);
    int updateBannerLink(Integer id, String linkUrl);
}
