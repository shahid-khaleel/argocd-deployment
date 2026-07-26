package com.shahid.gitopsdemo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GitopsDemoApplication {

    private static final Logger log = LoggerFactory.getLogger(GitopsDemoApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(GitopsDemoApplication.class, args);
        log.info("gitops-demo application started successfully");
    }
}
