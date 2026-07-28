package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoBeans;

@Configuration
@MockitoBeans({@MockitoBean(types = Front50Service.class)})
public class TestConfigurationForArtifacts {

  @Bean
  public ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
