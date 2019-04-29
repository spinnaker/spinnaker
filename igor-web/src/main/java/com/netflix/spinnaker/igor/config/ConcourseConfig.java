package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.igor.IgorConfigurationProperties;
import com.netflix.spinnaker.igor.concourse.ConcourseCache;
import com.netflix.spinnaker.igor.concourse.service.ConcourseService;
import com.netflix.spinnaker.igor.service.BuildServices;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty("concourse.enabled")
@EnableConfigurationProperties(ConcourseProperties.class)
public class ConcourseConfig {
  @Bean
  public Map<String, ConcourseService> concourseMasters(
      BuildServices buildServices,
      ConcourseCache concourseCache,
      IgorConfigurationProperties igorConfigurationProperties,
      @Valid ConcourseProperties concourseProperties) {
    List<ConcourseProperties.Host> masters = concourseProperties.getMasters();
    if (masters == null) {
      return Collections.emptyMap();
    }

    Map<String, ConcourseService> concourseMasters =
        masters.stream()
            .map(ConcourseService::new)
            .collect(Collectors.toMap(ConcourseService::getMaster, Function.identity()));

    buildServices.addServices(concourseMasters);
    return concourseMasters;
  }
}
