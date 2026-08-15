package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.NewsEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.AuditEventService;
import com.vihu.ganlu.service.NewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/news")
public class NewsAction {
    @Autowired
    NewService newsService;

    @Autowired
    AuditEventService auditEventService;

    // 获取前三个最新
    @PublicEndpoint
    @GetMapping("/limit")
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
    @GetMapping("/list")
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
    @PostMapping("/get")
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
    @PostMapping("/add")
    public ResponseEntity<?> addNews(
            @RequestBody NewsEntity news,
            HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        news.setImageUploadUserId(actor.getId());
        int i = newsService.addNews(news, actor);
        if(i>0){
            audit(actor, "NEWS_CREATE", "NEWS", news.getId(), "SUCCESS", null);
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
    @PutMapping("/update")
    public ResponseEntity<?> updateNews(
            @RequestBody NewsEntity news,
            HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        news.setImageUploadUserId(actor.getId());
        int i = newsService.updateNews(news, actor);
        if(i>0){
            audit(actor, "NEWS_UPDATE", "NEWS", news.getId(), "SUCCESS", null);
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
    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteNews(@RequestBody NewsEntity news, HttpServletRequest request) {
        UserEntity actor = currentUser(request);
        int i = newsService.deleteNews(news.getId(), actor);
        if(i>0){
            audit(actor, "NEWS_DELETE", "NEWS", news.getId(), "SUCCESS", null);
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

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

    private void audit(UserEntity actor, String action, String resourceType, Object resourceId,
                       String outcome, String reasonCode) {
        if (auditEventService != null) {
            auditEventService.record(actor, action, resourceType, resourceId, outcome, reasonCode);
        }
    }
}
