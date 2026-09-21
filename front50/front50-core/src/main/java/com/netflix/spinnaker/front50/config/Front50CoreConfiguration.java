/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.front50.config;

import com.netflix.spinnaker.front50.jackson.Front50ApiModule;
import com.netflix.spinnaker.moniker.Namer;
import com.netflix.spinnaker.moniker.frigga.FriggaReflectiveNamer;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import tools.jackson.databind.JacksonModule;

@Configuration
@EnableConfigurationProperties({FiatConfigurationProperties.class})
public class Front50CoreConfiguration {

  @Bean
  @ConditionalOnMissingBean(RestTemplate.class)
  public RestTemplate restTemplate() {
    return new RestTemplate();
  }

  @Bean
  @ConditionalOnMissingBean(Namer.class)
  public Namer<?> namer() {
    return new FriggaReflectiveNamer();
  }

  @Bean
  public JsonMapperBuilderCustomizer defaultObjectMapperCustomizer(List<JacksonModule> modules) {
    return jsonMapperBuilder -> {
      modules.addAll(List.of(new Front50ApiModule()));
      jsonMapperBuilder.addModules(modules);
    };
  }
}
