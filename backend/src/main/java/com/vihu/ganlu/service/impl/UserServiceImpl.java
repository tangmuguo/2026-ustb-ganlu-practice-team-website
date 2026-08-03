package com.vihu.ganlu.service.impl;

import com.vihu.ganlu.entitys.UserEntity;
import com.vihu.ganlu.entitys.UserQueryVo;
import com.vihu.ganlu.mappers.UserMapper;
import com.vihu.ganlu.service.UserService;
import com.vihu.ganlu.utils.ConflictException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<UserEntity> findAllUser() { return userMapper.findAllUser(); }

    @Override
    public UserEntity findUserById(int id) { return userMapper.findUserById(id); }

    @Override
    public List<UserEntity> findUserByLevel(int level) { return userMapper.findUserByLevel(level); }

    @Override
    public List<UserEntity> findUserBigLevel(int level) { return userMapper.findUserBigLevel(level); }

    @Override
    @Transactional
    public UserEntity authenticate(String username, String rawPassword) {
        if (username == null || rawPassword == null) return null;
        UserEntity user = userMapper.findUserByUsername(username.trim());
        if (user == null || user.getPassword() == null) return null;

        String storedPassword = user.getPassword();
        boolean matches;
        if (isBcrypt(storedPassword)) {
            matches = passwordEncoder.matches(rawPassword, storedPassword);
        } else {
            matches = MessageDigest.isEqual(
                    rawPassword.getBytes(StandardCharsets.UTF_8),
                    storedPassword.getBytes(StandardCharsets.UTF_8));
            if (matches) {
                userMapper.updatePasswordById(user.getId(), passwordEncoder.encode(rawPassword));
            }
        }

        return matches ? user : null;
    }

    @Override
    public boolean usernameExists(String username) {
        return username != null && userMapper.countByUsername(username.trim()) > 0;
    }

    @Override
    public boolean phoneExists(String phone) {
        return phone != null && userMapper.countByPhone(phone.trim()) > 0;
    }

    @Override
    public int findCountUserByPage(UserQueryVo vo) { return userMapper.findCountUserByPage(vo); }

    @Override
    public List<UserEntity> findUserByPage(UserQueryVo vo) { return userMapper.findUserByPage(vo); }

    @Override
    public Integer addUser(UserEntity user) {
        if (user == null) throw new IllegalArgumentException("用户信息不能为空");
        if (user.getUsername() == null || user.getUsername().trim().length() < 3
                || user.getUsername().trim().length() > 30) {
            throw new IllegalArgumentException("账号长度应为3到30个字符");
        }
        if (usernameExists(user.getUsername())) throw new ConflictException("账号已存在");
        if (user.getPhone() != null && !user.getPhone().trim().isEmpty() && phoneExists(user.getPhone())) {
            throw new ConflictException("手机号已存在");
        }
        if (user.getPassword() == null || user.getPassword().length() < 8 || user.getPassword().length() > 72) {
            throw new IllegalArgumentException("密码长度应为8到72个字符");
        }
        user.setUsername(user.getUsername().trim());
        user.setPhone(user.getPhone() == null ? null : user.getPhone().trim());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userMapper.addUser(user);
    }

    @Override
    public Integer updateUserById(UserEntity user) {
        if (user == null || user.getId() == null) throw new IllegalArgumentException("用户编号不能为空");
        if (user.getUsername() == null || user.getUsername().trim().length() < 3
                || user.getUsername().trim().length() > 30) {
            throw new IllegalArgumentException("账号长度应为3到30个字符");
        }
        user.setUsername(user.getUsername().trim());
        String submittedPassword = user.getPassword();
        if (submittedPassword == null || submittedPassword.trim().isEmpty()) {
            user.setPassword(null);
        } else if (submittedPassword.length() < 8 || submittedPassword.length() > 72) {
            throw new IllegalArgumentException("密码长度应为8到72个字符");
        } else {
            // 更新接口只接收明文密码。即使输入伪装成 BCrypt，也必须重新哈希。
            user.setPassword(passwordEncoder.encode(submittedPassword));
        }
        return userMapper.updateUserById(user);
    }

    @Override
    @Transactional
    public Integer deleteUserByIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) throw new IllegalArgumentException("用户编号不能为空");
        if (userMapper.countTeamBindingsByUserIds(ids) > 0) {
            throw new ConflictException("所选团队账号已绑定团队内容，请先归档、迁移或解绑团队后再删除");
        }
        return userMapper.deleteUserByIds(ids);
    }

    private boolean isBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$");
    }
}
