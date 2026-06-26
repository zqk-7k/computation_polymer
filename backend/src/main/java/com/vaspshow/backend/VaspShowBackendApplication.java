package com.vaspshow.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VaspShowBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(VaspShowBackendApplication.class, args);
  }
}
