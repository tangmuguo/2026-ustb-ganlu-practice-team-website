package com.vihu.ganlu.entitys;

public class LoginResponse {
    private String token;
    private long expiresIn;
    private UserSummary user;

    public LoginResponse(String token, long expiresIn, UserSummary user) {
        this.token = token;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public UserSummary getUser() {
        return user;
    }
}
