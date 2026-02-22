package com.example.uniactivity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UniactivityApplication {

	public static void main(String[] args) {
		SpringApplication.run(UniactivityApplication.class, args);
	}

}
