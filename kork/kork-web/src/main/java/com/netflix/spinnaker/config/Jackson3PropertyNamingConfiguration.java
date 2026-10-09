/*
 * Copyright 2026 spinnaker.io
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

import com.netflix.spinnaker.kork.jackson.Jackson2AccessorNamingStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;

/** Restores legacy bean property names independently of service-specific property ordering. */
@Configuration
@ConditionalOnClass({JsonMapperBuilderCustomizer.class, ObjectMapper.class})
@ConditionalOnProperty(
    name = "spinnaker.jackson.legacy-bean-naming",
    havingValue = "true",
    matchIfMissing = true)
public class Jackson3PropertyNamingConfiguration {
  @Bean
  @Order(-1)
  public JsonMapperBuilderCustomizer jackson2PropertyNamingCustomizer() {
    return builder ->
        builder
            .accessorNaming(new Jackson2AccessorNamingStrategy.Provider())
            .disable(MapperFeature.FIX_FIELD_NAME_UPPER_CASE_PREFIX);
  }
}
