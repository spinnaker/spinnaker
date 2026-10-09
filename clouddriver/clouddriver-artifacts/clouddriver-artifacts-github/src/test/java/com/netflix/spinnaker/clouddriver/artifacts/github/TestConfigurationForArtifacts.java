package com.netflix.spinnaker.clouddriver.artifacts.github;

import com.netflix.spinnaker.clouddriver.core.services.Front50Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoBeans;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@MockitoBeans({@MockitoBean(types = Front50Service.class)})
public class TestConfigurationForArtifacts {

  @Bean
  public ObjectMapper objectMapper() {
    return JsonMapper.builder().build();
  }
}
