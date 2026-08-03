package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.BannerEntity;

import java.util.List;

public interface BannerMapper {
    List<BannerEntity> findAll();
    BannerEntity findById(Integer id);
    int count();
    int insert(BannerEntity banner);
    int update(BannerEntity banner);
    int delete(Integer id);
    int updateSort(Integer id, Integer sortOrder);
    int updateStatus(Integer id, Integer isVisible);
    int updateLink(Integer id, String linkUrl);
}
