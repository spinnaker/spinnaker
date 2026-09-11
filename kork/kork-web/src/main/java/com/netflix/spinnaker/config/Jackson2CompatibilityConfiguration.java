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
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.AnnotationIntrospector;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;

/**
 * Keeps Jackson 2 databind annotations working on Boot 4's Jackson 3 mappers.
 *
 * <p>Chains {@link Jackson2BuilderAnnotationIntrospector} behind Jackson 3's own introspector so
 * builder-based models ({@code @JsonDeserialize(builder = ...)} / {@code @JsonPOJOBuilder}) keep
 * deserializing exactly as they did on Boot 3. Jackson 3-native annotations always win; the bridge
 * only fills gaps.
 */
@Configuration
@ConditionalOnClass({JsonMapperBuilderCustomizer.class, tools.jackson.databind.ObjectMapper.class})
public class Jackson2CompatibilityConfiguration {

  @Bean
  public JsonMapperBuilderCustomizer jackson2AnnotationCompatibilityCustomizer() {
    return builder -> {
      AnnotationIntrospector current = builder.annotationIntrospector();
      if (current == null) {
        current = new JacksonAnnotationIntrospector();
      }
      builder.annotationIntrospector(
          new AnnotationIntrospectorPair(current, new Jackson2BuilderAnnotationIntrospector()));
    };
  }
}
