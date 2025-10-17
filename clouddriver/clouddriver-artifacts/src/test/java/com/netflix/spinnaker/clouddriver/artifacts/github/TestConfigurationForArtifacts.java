package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.MockBeans;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MockBeans({@MockBean(Front50Service.class)})
public class TestConfigurationForArtifacts {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
