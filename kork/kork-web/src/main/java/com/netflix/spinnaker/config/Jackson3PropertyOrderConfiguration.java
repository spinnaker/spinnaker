/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.MapperFeature;

/**
 * Restores Jackson 2 (Boot 3) JSON property ordering on Boot 4's Jackson 3 mappers.
 *
 * <p>Jackson 3 enables {@code SORT_PROPERTIES_ALPHABETICALLY} by default, which reorders every
 * serialized API response compared to Boot 3's declaration order. Spinnaker's API consumers and
 * contract tests expect declaration order, so every Boot-built Jackson 3 mapper disables it unless
 * a service explicitly opts into sorting via {@code
 * spring.jackson.mapper.SORT_PROPERTIES_ALPHABETICALLY} (e.g. gate, clouddriver).
 */
@Configuration
@ConditionalOnClass({JsonMapperBuilderCustomizer.class, tools.jackson.databind.ObjectMapper.class})
@ConditionalOnProperty(
    name = "spring.jackson.mapper.SORT_PROPERTIES_ALPHABETICALLY",
    havingValue = "false",
    matchIfMissing = true)
public class Jackson3PropertyOrderConfiguration {

  @Bean
  public JsonMapperBuilderCustomizer jackson2PropertyOrderCustomizer() {
    return builder -> builder.disable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY);
  }
}
