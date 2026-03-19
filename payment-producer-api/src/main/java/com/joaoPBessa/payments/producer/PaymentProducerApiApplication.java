package com.joaoPBessa.payments.producer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class PaymentProducerApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PaymentProducerApiApplication.class, args);
	}

}
