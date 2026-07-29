package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.UserService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    @Resource
    UserMapper userMapper;

    @Override
    public List<UserEntity> findAllUser() {
        return userMapper.findAllUser();
    }

    @Override
    public UserEntity findUserById(int id) {
        return userMapper.findUserById(id);
    }

    @Override
    public List<UserEntity> findUserByLevel(int level) {
        return userMapper.findUserByLevel(level);
    }

    @Override
    public List<UserEntity> findUserBigLevel(int level) {
        return userMapper.findUserBigLevel(level);
    }

    @Override
    public UserEntity login(UserEntity e) {
        return userMapper.login(e);
    }

    @Override
    public int findCountUserByPage(UserQueryVo vo) {
        return userMapper.findCountUserByPage(vo);
    }

    @Override
    public List<UserEntity> findUserByPage(UserQueryVo vo) {
        return userMapper.findUserByPage(vo);
    }

    @Override
    public Integer addUser(UserEntity e) {
        return userMapper.addUser(e);
    }

    @Override
    public Integer updateUserById(UserEntity e) {
        return userMapper.updateUserById(e);
    }

    @Override
    public Integer deleteUserByIds(List<Integer> ids) {
        return userMapper.deleteUserByIds(ids);
    }
}
