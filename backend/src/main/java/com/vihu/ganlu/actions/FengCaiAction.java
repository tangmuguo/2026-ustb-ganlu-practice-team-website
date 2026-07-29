package com.vihu.ganlu.actions;

import com.google.common.collect.ImmutableMap;
import com.vihu.ganlu.entitys.TeamPageImageEntity;
import com.vihu.ganlu.entitys.TeamPageWordEntity;
import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.security.AuthInterceptor;
import com.vihu.ganlu.security.PublicEndpoint;
import com.vihu.ganlu.security.RequireRoles;
import com.vihu.ganlu.service.TeamPageImageService;
import com.vihu.ganlu.service.TeamPageWordService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/fengcai")
public class FengCaiAction {
    @Resource
    TeamPageWordService teamPageWordService;
    @Resource
    TeamPageImageService teamPageImageService;
    @RequireRoles({0, 1})
    @RequestMapping("/uploadImage")
    public ResponseEntity<?> uploadImage(@RequestParam("imageFile") MultipartFile imageFile){
        String imagePath = teamPageImageService.uploadTeamImage(imageFile);

        if (imagePath!=null) {
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功",
                    "content",imagePath
            ));
        } else {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @RequestMapping("/addImage")
    public ResponseEntity<?> addImage(@RequestBody TeamPageImageEntity entity, HttpServletRequest request){
        UserEntity currentUser = currentUser(request);
        if (currentUser.getLevel() == 1 || entity.getUserId() == null) {
            entity.setUserId(currentUser.getId());
        }
        int i = teamPageImageService.insertTeamImage(entity);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @RequestMapping("/addWord")
    public ResponseEntity<?> addWord(@RequestBody TeamPageWordEntity entity, HttpServletRequest request){
        UserEntity currentUser = currentUser(request);
        if (currentUser.getLevel() == 1 || entity.getUserId() == null) {
            entity.setUserId(currentUser.getId());
        }
        int i = teamPageWordService.insertTeamWord(entity);
        if(i>0){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "上传成功"
            ));
        }else{
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "上传失败"
            ));
        }
    }

    @PublicEndpoint
    @RequestMapping("/words")
    public ResponseEntity<?> findAllWord(@RequestBody TeamPageWordEntity entity){
        List<TeamPageWordEntity> allWord = teamPageWordService.findAllWord(entity.getUserId());
        if(allWord!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content",allWord
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 400,
                    "message", "查询失败"
            ));
        }
    }

    @PublicEndpoint
    @RequestMapping("/images")
    public ResponseEntity<?> findAllImage(@RequestBody TeamPageImageEntity entity){
        List<TeamPageImageEntity> allWord = teamPageImageService.findAllImages(entity.getUserId());
        if(allWord!=null){
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 200,
                    "message", "查询成功",
                    "content",allWord
            ));
        }else{
            return ResponseEntity.ok().body(ImmutableMap.of(
                    "code", 400,
                    "message", "查询失败"
            ));
        }
    }

    @RequireRoles({0, 1})
    @RequestMapping("/deleteImage")
    public ResponseEntity<?> deleteImage(@RequestBody List<Integer> ids, HttpServletRequest request){
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "请选择要删除的记录"
            ));
        }
        UserEntity currentUser = currentUser(request);
        int i = currentUser.getLevel() == 0
                ? teamPageImageService.deleteTeamPageImageByIds(ids)
                : teamPageImageService.deleteTeamPageImageByIdsAndUserId(ids, currentUser.getId());
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

    @RequireRoles({0, 1})
    @RequestMapping("/deleteWord")
    public ResponseEntity<?> deleteWord(@RequestBody List<Integer> ids, HttpServletRequest request){
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ImmutableMap.of(
                    "code", 400,
                    "message", "请选择要删除的记录"
            ));
        }
        UserEntity currentUser = currentUser(request);
        int i = currentUser.getLevel() == 0
                ? teamPageWordService.deleteTeamPageWordByIds(ids)
                : teamPageWordService.deleteTeamPageWordByIdsAndUserId(ids, currentUser.getId());
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

    private UserEntity currentUser(HttpServletRequest request) {
        return (UserEntity) request.getAttribute(AuthInterceptor.CURRENT_USER_ATTRIBUTE);
    }

}
