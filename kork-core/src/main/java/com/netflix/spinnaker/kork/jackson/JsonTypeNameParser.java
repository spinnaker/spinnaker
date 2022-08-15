/*
 * Copyright 2022 Apple, Inc.
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

package com.netflix.spinnaker.kork.jackson;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

/**
 * Parses {@link JsonTypeName} annotations on classes to determine the type discriminator used in
 * {@link ObjectMapperSubtypeConfigurer}.
 */
@Log4j2
@RequiredArgsConstructor
public class JsonTypeNameParser implements NamedTypeParser {
  private final boolean strictSerialization;

  @Nullable
  @Override
  public NamedType parse(@Nonnull Class<?> type) {
    JsonTypeName nameAnnotation = type.getAnnotation(JsonTypeName.class);
    if (nameAnnotation == null || "".equals(nameAnnotation.value())) {
      String message =
          "Subtype " + type.getSimpleName() + " does not have a JsonTypeName annotation";
      if (strictSerialization) {
        throw new InvalidSubtypeConfigurationException(message);
      }
      log.warn(message);
      return null;
    }

    return new NamedType(type, nameAnnotation.value());
  }
}
