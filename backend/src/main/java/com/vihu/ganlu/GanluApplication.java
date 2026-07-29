package com.vihu.ganlu;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.vihu.ganlu.mappers")
public class GanluApplication {

    public static void main(String[] args) {
        SpringApplication.run(GanluApplication.class, args);
    }

}
