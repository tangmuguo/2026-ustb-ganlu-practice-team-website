package com.vihu.ganlu.actions;


import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.BannerEntity;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.BannerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/banner")
public class BannerAction {
    @Resource
    private BannerService bannerService;

    @PublicEndpoint
    @RequestMapping("/list")
    public ResponseEntity<?> getBannerList() {
        List<BannerEntity> banners = bannerService.getAllBanners();
        if(banners!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content",banners
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "查询失败"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/add")
    public ResponseEntity<?> addBanner(@RequestBody BannerEntity banner) {
        if (bannerService.getBannerCount() >= 5) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 202,
                    "message", "轮播图最多只能添加5张"
            ));
        }
        int i = bannerService.addBanner(banner);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "添加成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "添加失败"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/update")
    public ResponseEntity<?> updateBanner(@RequestBody BannerEntity banner) {
        int i = bannerService.updateBanner(banner);

        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "更新成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "更新失败"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/delete")
    public ResponseEntity<?> deleteBanner(@RequestBody BannerEntity banner) {
        int i = bannerService.deleteBanner(banner.getId());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "删除成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "删除失败"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/updateSort")
    public ResponseEntity<?> updateBannerSort(@RequestBody BannerEntity banner) {
        int i = bannerService.updateBannerSort(banner.getId(), banner.getSortOrder());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "更新成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "更新失败"
            ));
        }
    }

    @RequireRoles({0})
    @RequestMapping("/updateStatus")
    public ResponseEntity<?> updateBannerStatus(@RequestBody BannerEntity banner) {
        int i = bannerService.updateBannerStatus(banner.getId(), banner.getIsVisible());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "更新成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "更新失败"
            ));
        }

    }

    @RequireRoles({0})
    @RequestMapping("/updateLink")
    public ResponseEntity<?> updateBannerLink(@RequestBody BannerEntity banner) {
        int i = bannerService.updateBannerLink(banner.getId(), banner.getLinkUrl());
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "更新成功"
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "更新失败"
            ));
        }
    }
}
