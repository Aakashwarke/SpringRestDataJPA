package com.springlaunch;

import com.springlaunch.config.SpringLaunchProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(SpringLaunchProperties.class)
public class SpringLaunchApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringLaunchApplication.class, args);
    }
}
