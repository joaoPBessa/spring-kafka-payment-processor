package com.joaoPBessa.payments.producer;

import org.springframework.boot.SpringApplication;

public class TestPaymentProducerApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(PaymentProducerApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
