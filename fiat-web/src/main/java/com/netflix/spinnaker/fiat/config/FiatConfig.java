package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.model.DefaultPermissionsRepository;
import com.netflix.spinnaker.fiat.model.PermissionsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FiatConfig {

  @Bean
  PermissionsRepository permissionsRepository() {
    return new DefaultPermissionsRepository();
  }
}
