package com.vihu.ganlu.entitys;

import lombok.Data;

import java.util.Date;

@Data
public class NewsEntity {
    int id;
    String caption;
    String content;
    String imageUrl;
    Date createAt;
    String linkUrl;
}
