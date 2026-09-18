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

import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.NopAnnotationIntrospector;

/**
 * Fallback introspector that honors Jackson 2 databind annotations which Jackson 3 no longer reads,
 * restoring Boot 3 deserialization behavior on Boot 4's Jackson 3 mappers.
 *
 * <p>Covered: {@code @JsonDeserialize(builder = ...)} and {@code @JsonPOJOBuilder} (Lombok builders
 * and hand-written builders across Spinnaker's models). Jackson 3 only understands its own {@code
 * tools.jackson.databind.annotation} twins; without this bridge, builder-based models fail with "no
 * Creators". Only consulted when Jackson 3's own annotations say nothing.
 */
public class Jackson2BuilderAnnotationIntrospector extends NopAnnotationIntrospector {

  @Override
  public Class<?> findPOJOBuilder(MapperConfig<?> config, AnnotatedClass ac) {
    com.fasterxml.jackson.databind.annotation.JsonDeserialize ann =
        ac.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonDeserialize.class);
    if (ann == null) {
      return null;
    }
    try {
      Class<?> builder = ann.builder();
      return (builder == null || builder == Void.class) ? null : builder;
    } catch (TypeNotPresentException | NoClassDefFoundError e) {
      return null;
    }
  }

  @Override
  public tools.jackson.databind.annotation.JsonPOJOBuilder.Value findPOJOBuilderConfig(
      MapperConfig<?> config, AnnotatedClass ac) {
    com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder ann =
        ac.getAnnotation(com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder.class);
    if (ann == null) {
      return null;
    }
    return new tools.jackson.databind.annotation.JsonPOJOBuilder.Value(
        ann.buildMethodName(), ann.withPrefix());
  }
}
