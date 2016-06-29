package com.netflix.spinnaker.fiat.config;

import com.netflix.spinnaker.fiat.permissions.InMemoryPermissionsRepository;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FiatConfig {

  @Bean
  PermissionsRepository permissionsRepository() {
    return new InMemoryPermissionsRepository();
  }
}
