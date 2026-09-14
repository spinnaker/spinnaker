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

package com.netflix.kayenta.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.kork.ClassScanner;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.jsontype.NamedType;

/**
 * Registers kayenta's polymorphic subtypes (e.g. metrics query configs such as {@code
 * "prometheus"}) on Boot 4's Jackson 3 mapper.
 *
 * <p>The Jackson 2 {@code kayentaObjectMapper} registers them via {@link
 * ObjectMapperSubtypeConfigurer}, which Jackson 3 ignores; without this, requests like {@code POST
 * /standalone_canary_analysis} fail with "could not resolve type id".
 */
@Configuration
@ConditionalOnClass(JsonMapperBuilderCustomizer.class)
public class KayentaJackson3SubtypeConfiguration {

  @Bean
  public JsonMapperBuilderCustomizer kayentaJackson3SubtypeRegistrar(
      List<ObjectMapperSubtypeConfigurer.SubtypeLocator> subtypeLocators) {
    return builder -> {
      for (ObjectMapperSubtypeConfigurer.SubtypeLocator locator : subtypeLocators) {
        for (String pkg : locator.searchPackages()) {
          for (Class<?> subtype :
              ClassScanner.forBaseType(locator.rootType()).addLoadablePackage(pkg).scan()) {
            JsonTypeName name = subtype.getAnnotation(JsonTypeName.class);
            if (name != null && !name.value().isEmpty()) {
              builder.registerSubtypes(new NamedType(subtype, name.value()));
            }
          }
        }
      }
    };
  }
}
