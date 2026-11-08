package com.team.resume;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CareerFlow-AI 智能求职助手 后端启动类
 */
@SpringBootApplication
@MapperScan({"com.team.resume.mapper", "com.team.resume.auth.mapper"})
public class CareerFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(CareerFlowApplication.class, args);
    }
}
