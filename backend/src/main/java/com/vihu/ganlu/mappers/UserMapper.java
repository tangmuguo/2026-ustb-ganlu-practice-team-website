package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;

import java.util.List;

public interface UserMapper {

    List<UserEntity> findAllUser();
    UserEntity findUserById(int id);
    List<UserEntity> findUserByLevel(int level);
    List<UserEntity> findUserBigLevel(int level);
    UserEntity login(UserEntity e);
    int findCountUserByPage(UserQueryVo vo);
    List<UserEntity> findUserByPage(UserQueryVo vo);
    Integer addUser(UserEntity e);
    Integer updateUserById(UserEntity e);
    Integer deleteUserByIds(List<Integer> ids);

}
