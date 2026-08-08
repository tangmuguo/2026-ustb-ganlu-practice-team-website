package com.vihu.ganlu.security;

import com.vihu.ganlu.entitys.UserEntity;
import org.springframework.stereotype.Component;

@Component
public class AuthContext {
    private final ThreadLocal<UserEntity> currentUser = new ThreadLocal<>();

    public void setCurrentUser(UserEntity user) {
        currentUser.set(user);
    }

    public UserEntity getCurrentUser() {
        return currentUser.get();
    }

    public Integer getCurrentUserId() {
        UserEntity user = currentUser.get();
        return user == null ? null : user.getId();
    }

    public void clear() {
        currentUser.remove();
    }
}
