package com.vihu.ganlu.utils;

import com.vihu.ganlu.entitys.ResultEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ResultUtil {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static String toJsonString(int code,Object obj){
        ResultEntity re=new ResultEntity();
        re.setCode(code);

        String m = getReslustMessage(code);
        re.setMessage(m);

        re.setContent(obj);

        try {
            return OBJECT_MAPPER.writeValueAsString(re);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("响应序列化失败", exception);
        }
    }

    private static String getReslustMessage(int code){
        String m="";
        switch (code){
            case 200:m="操作成功";break;
            case 201:m="账号或者密码错误";break;
            case 202:m="账号已存在";break;
            case 203:m="添加商品失败";break;
            case 204:m="删除商品失败";break;
            case 205:m="修改商品失败";break;
            default:m="未知错误";
        }
        return m;
    }
}
