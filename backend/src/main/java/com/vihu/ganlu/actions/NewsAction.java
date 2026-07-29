package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.NewsEntity;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.NewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/news")
public class NewsAction {
    @Autowired
    NewService newsService;

    // 获取前三个最新
    @PublicEndpoint
    @RequestMapping("/limit")
    public ResponseEntity<?> getNewsLimit() {
        List<NewsEntity> newsList = newsService.getNewsList(3);
        if(newsList!=null && newsList.size()>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content", newsList
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询失败"
            ));
        }
    }

    // 获取新闻列表
    @PublicEndpoint
    @RequestMapping("/list")
    public ResponseEntity<?> getNewsList() {
        List<NewsEntity> newsList = newsService.findAll();
        if(newsList!=null && newsList.size()>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content", newsList
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "查询无记录"
            ));
        }
    }

    // 获取新闻详情
    @PublicEndpoint
    @RequestMapping("/get")
    public ResponseEntity<?> getNewsById(@RequestBody NewsEntity entity) {
        NewsEntity news = newsService.getNewsById(entity.getId());
        if(news!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content", news
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 201,
                    "message", "查询失败"
            ));
        }
    }

    // 添加新闻
    @RequireRoles({0})
    @RequestMapping("/add")
    public ResponseEntity<?> addNews(@RequestBody NewsEntity news) {
        int i = newsService.addNews(news);
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

    // 修改新闻
    @RequireRoles({0})
    @RequestMapping("/update")
    public ResponseEntity<?> updateNews(@RequestBody NewsEntity news) {
        int i = newsService.updateNews(news);
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

    // 删除新闻
    @RequireRoles({0})
    @RequestMapping("/delete")
    public ResponseEntity<?> deleteNews(@RequestBody NewsEntity news) {
        int i = newsService.deleteNews(news.getId());
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
}
