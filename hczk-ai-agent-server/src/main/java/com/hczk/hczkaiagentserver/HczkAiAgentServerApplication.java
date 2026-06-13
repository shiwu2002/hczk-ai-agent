package com.hczk.hczkaiagentserver;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.hczk.hczkaiagentserver.mapper")
public class HczkAiAgentServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(HczkAiAgentServerApplication.class, args);
    }

}
