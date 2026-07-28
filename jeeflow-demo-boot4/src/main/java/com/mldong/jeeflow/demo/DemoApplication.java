package com.mldong.jeeflow.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * jeeflow 演示站
 *
 * <p>启动后访问 http://localhost:8080 即可体验工作流全流程</p>
 */
@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
