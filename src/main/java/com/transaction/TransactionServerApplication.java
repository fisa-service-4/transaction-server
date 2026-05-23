package com.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class TransactionServerApplication {

  public static void main(String[] args) {
    SpringApplication.run(TransactionServerApplication.class, args);
  }
}
