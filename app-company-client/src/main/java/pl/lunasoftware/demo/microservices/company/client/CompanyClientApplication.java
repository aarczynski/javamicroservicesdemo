package pl.lunasoftware.demo.microservices.company.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class CompanyClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompanyClientApplication.class, args);
	}

}
