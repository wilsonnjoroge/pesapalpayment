package com.pesapal.pesapalpayment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
//import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication //(exclude = { DataSourceAutoConfiguration.class })
@RestController
public class PesapalpaymentApplication {

	public static void main(String[] args) {
		SpringApplication.run(PesapalpaymentApplication.class, args);
	}

	@GetMapping("/test")
	public String testEndpoint() {
		return "Pesapal project is running OK!";
	}

}
