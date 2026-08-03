package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;

import java.util.List;
import org.apache.ibatis.annotations.Param;

public interface UserMapper {

    List<UserEntity> findAllUser();
    UserEntity findUserById(int id);
    List<UserEntity> findUserByLevel(int level);
    List<UserEntity> findUserBigLevel(int level);
    UserEntity findUserByUsername(@Param("username") String username);
    int countByUsername(@Param("username") String username);
    int countByPhone(@Param("phone") String phone);
    int updatePasswordById(@Param("id") Integer id, @Param("password") String password);
    int findCountUserByPage(UserQueryVo vo);
    List<UserEntity> findUserByPage(UserQueryVo vo);
    Integer addUser(UserEntity e);
    Integer updateUserById(UserEntity e);
    int countTeamBindingsByUserIds(@Param("ids") List<Integer> ids);
    Integer deleteUserByIds(List<Integer> ids);

}
