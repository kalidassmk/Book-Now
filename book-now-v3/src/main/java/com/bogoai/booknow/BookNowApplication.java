package com.bogoai.booknow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BookNowApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookNowApplication.class, args);
	}

}
