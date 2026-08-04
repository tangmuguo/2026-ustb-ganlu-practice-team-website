package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.BannerEntity;
import com.vihu.ganlu.mappers.BannerMapper;
import com.vihu.ganlu.service.BannerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BannerServiceImpl implements BannerService {
    private final BannerMapper bannerMapper;
    private final PublicImageLifecycleService imageLifecycleService;

    public BannerServiceImpl(
            BannerMapper bannerMapper,
            PublicImageLifecycleService imageLifecycleService) {
        this.bannerMapper = bannerMapper;
        this.imageLifecycleService = imageLifecycleService;
    }

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
        requireUploadOwner(banner);
        banner.setImageUrl(imageLifecycleService.promote(
                banner.getImageUploadUserId(), banner.getImageUploadToken()));
        int inserted = bannerMapper.insert(banner);
        if (inserted != 1) throw new IllegalStateException("保存轮播图失败");
        return inserted;
    }

    @Override
    @Transactional
    public int updateBanner(BannerEntity banner) {
        BannerEntity existing = banner == null || banner.getId() == null
                ? null : bannerMapper.findByIdForUpdate(banner.getId());
        if (existing == null) return 0;
        String oldPath = existing.getImageUrl();
        boolean replacingImage = hasUploadToken(banner);
        if (replacingImage) {
            requireUploadOwner(banner);
            imageLifecycleService.deletePublicImageAfterCommit(oldPath);
            banner.setImageUrl(imageLifecycleService.promote(
                    banner.getImageUploadUserId(), banner.getImageUploadToken()));
        } else {
            imageLifecycleService.requireManagedImageAsset(oldPath);
            banner.setImageUrl(oldPath);
        }
        int updated = bannerMapper.update(banner);
        if (updated != 1 && replacingImage) throw new IllegalStateException("更新轮播图失败");
        return updated;
    }

    @Override
    @Transactional
    public int deleteBanner(Integer id) {
        BannerEntity existing = id == null ? null : bannerMapper.findByIdForUpdate(id);
        if (existing == null) return 0;
        int deleted = bannerMapper.delete(id);
        if (deleted == 1) {
            imageLifecycleService.deletePublicImageAfterCommit(existing.getImageUrl());
        }
        return deleted;
    }

    @Override
    @Transactional
    public int updateBannerSort(Integer id, Integer sortOrder) {
        return bannerMapper.updateSort(id, sortOrder);
    }

    @Override
    @Transactional
    public int updateBannerStatus(Integer id, Integer isVisible) {
        BannerEntity existing = id == null ? null : bannerMapper.findByIdForUpdate(id);
        if (existing == null) return 0;
        if (Integer.valueOf(1).equals(isVisible)) {
            imageLifecycleService.requireManagedImageAsset(existing.getImageUrl());
        }
        return bannerMapper.updateStatus(id, isVisible);
    }

    @Override
    @Transactional
    public int updateBannerLink(Integer id, String linkUrl) {
        return bannerMapper.updateLink(id, linkUrl);
    }

    private boolean hasUploadToken(BannerEntity banner) {
        return banner != null && banner.getImageUploadToken() != null
                && !banner.getImageUploadToken().trim().isEmpty();
    }

    private void requireUploadOwner(BannerEntity banner) {
        if (!hasUploadToken(banner) || banner.getImageUploadUserId() == null) {
            throw new IllegalArgumentException("请先上传并暂存轮播图片");
        }
    }

}
