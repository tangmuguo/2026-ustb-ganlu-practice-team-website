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

    /**
     * @deprecated 已迁移到 POST /team-content/members / photos。旧前端不再调用。
     */
    @Deprecated
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

    /**
     * @deprecated 已迁移到 POST /team-content/members / photos。旧前端不再调用。
     */
    @Deprecated
    @RequireRoles({0, 1})
    @RequestMapping("/addImage")
    public ResponseEntity<?> addImage(@RequestBody TeamPageImageEntity entity, HttpServletRequest request){
        UserEntity currentUser = currentUser(request);
        if (currentUser.getLevel() == 1 || entity.getUserId() == null) {
            entity.setUserId(currentUser.getId());
        }
        entity.setTeamId(currentUser.getId()); // 新接口语义：teamId 从 Token 推导
        entity.setStatus("PENDING");
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

    /**
     * @deprecated 已迁移到 POST /team-content/logs / honors。旧前端不再调用。
     */
    @Deprecated
    @RequireRoles({0, 1})
    @RequestMapping("/addWord")
    public ResponseEntity<?> addWord(@RequestBody TeamPageWordEntity entity, HttpServletRequest request){
        UserEntity currentUser = currentUser(request);
        if (currentUser.getLevel() == 1 || entity.getUserId() == null) {
            entity.setUserId(currentUser.getId());
        }
        entity.setTeamId(currentUser.getId()); // 新接口语义：teamId 从 Token 推导
        entity.setStatus("PENDING");
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

    /**
     * @deprecated 已迁移到 GET /team-content/public/{teamId}。
     * 内部转发到新接口语义：entity.getUserId() 实际是 teamId。
     */
    @Deprecated
    @PublicEndpoint
    @RequestMapping("/words")
    public ResponseEntity<?> findAllWord(@RequestBody TeamPageWordEntity entity){
        int teamId = entity.getUserId();
        List<TeamPageWordEntity> allWord = teamPageWordService.findByTeamId(teamId);
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

    /**
     * @deprecated 已迁移到 GET /team-content/public/{teamId}。
     * 内部转发到新接口语义：entity.getUserId() 实际是 teamId。
     */
    @Deprecated
    @PublicEndpoint
    @RequestMapping("/images")
    public ResponseEntity<?> findAllImage(@RequestBody TeamPageImageEntity entity){
        int teamId = entity.getUserId();
        List<TeamPageImageEntity> allWord = teamPageImageService.findByTeamId(teamId);
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

    /**
     * @deprecated 已迁移到 POST /team-content/{type}/{id}/delete。旧前端不再调用。
     */
    @Deprecated
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
                ? teamPageImageService.archiveById(ids.get(0)) ? 1 : 0
                : teamPageImageService.archiveByIdAndTeamId(ids.get(0), currentUser.getId()) ? 1 : 0;
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

    /**
     * @deprecated 已迁移到 POST /team-content/{type}/{id}/delete。旧前端不再调用。
     */
    @Deprecated
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
                ? teamPageWordService.archiveById(ids.get(0)) ? 1 : 0
                : teamPageWordService.archiveByIdAndTeamId(ids.get(0), currentUser.getId()) ? 1 : 0;
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
