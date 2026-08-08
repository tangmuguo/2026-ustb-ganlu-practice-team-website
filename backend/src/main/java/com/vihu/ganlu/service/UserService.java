package com.vihu.ganlu.service;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;

import java.util.List;

public interface UserService {
    List<UserEntity> findAllUser();
    UserEntity findUserById(int id);
    List<UserEntity> findUserByLevel(int level);
    List<UserEntity> findUserBigLevel(int level);
    UserEntity authenticate(String username, String rawPassword);
    boolean usernameExists(String username);
    boolean phoneExists(String phone);
    int findCountUserByPage(UserQueryVo vo);
    List<UserEntity> findUserByPage(UserQueryVo vo);
    Integer addUser(UserEntity e);
    Integer updateUserById(UserEntity e);
    Integer deleteUserByIds(List<Integer> ids);
}
