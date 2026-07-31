package com.vihu.ganlu.mappers;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
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

    // ====================【新增新标准接口】====================
    /**
     * 批量根据用户id集合查询用户信息
     * 用于留言列表：一次性获取所有留言、回复所属用户，消除N+1联表查询
     * @param userIdList 用户id集合
     */
    List<UserEntity> selectUserByIdList(@Param("userIdList") List<Integer> userIdList);

}