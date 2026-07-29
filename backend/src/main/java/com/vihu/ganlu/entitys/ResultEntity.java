package com.vihu.ganlu.entitys;

public class ResultEntity {
    int code;         //404 无此页面  500 后端错误  200 成功   201账号或者密码错误   202账号已存在
    String message;   // 无此页面  后端错误 成功 账号或者密码错误
    Object content;   //JSON字符串

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }

}
