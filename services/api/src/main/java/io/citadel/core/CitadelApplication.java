package io.citadel.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CitadelApplication {

  public static void main(String[] args) {
    SpringApplication.run(CitadelApplication.class, args);
  }
}
