package com.insidergame.insider_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

//@EnableScheduling
@SpringBootApplication
public class InsiderApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(InsiderApiApplication.class, args);
	}

}
