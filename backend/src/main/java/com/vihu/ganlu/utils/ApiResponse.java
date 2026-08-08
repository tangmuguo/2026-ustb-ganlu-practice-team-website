package com.vihu.ganlu.utils;

public class ApiResponse<T> {
    private int code;
    private String message;
    private T content;

    public ApiResponse() {
    }

    public ApiResponse(int code, String message, T content) {
        this.code = code;
        this.message = message;
        this.content = content;
    }

    public static <T> ApiResponse<T> success(String message, T content) {
        return new ApiResponse<>(200, message, content);
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return new ApiResponse<>(code, message, null);
    }

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

    public T getContent() {
        return content;
    }

    public void setContent(T content) {
        this.content = content;
    }
}
