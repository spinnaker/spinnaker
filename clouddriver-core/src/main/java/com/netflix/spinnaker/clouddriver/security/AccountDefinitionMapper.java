/*
 * Copyright 2021 Apple Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.Map;
import java.util.Objects;

/**
 * Maps account definitions to and from strings. Only {@link CredentialsDefinition} classes
 * annotated with a {@link JsonTypeName} will be considered.
 */
@NonnullByDefault
public class AccountDefinitionMapper {

  /**
   * Returns the JSON type discriminator for a given class. Types are defined via the {@link
   * JsonTypeName} annotation.
   */
  public static String getJsonTypeName(Class<?> clazz) {
    var jsonTypeName = clazz.getAnnotation(JsonTypeName.class);
    return Objects.requireNonNull(jsonTypeName, "No @JsonTypeName for " + clazz).value();
  }

  private final ObjectMapper objectMapper;
  private final Map<String, Class<? extends CredentialsDefinition>> typeMap;

  public AccountDefinitionMapper(
      ObjectMapper objectMapper, Map<String, Class<? extends CredentialsDefinition>> typeMap) {
    this.objectMapper = objectMapper;
    this.typeMap = typeMap;
  }

  public String convertToString(CredentialsDefinition definition) throws JsonProcessingException {
    return objectMapper.writeValueAsString(definition);
  }

  public CredentialsDefinition convertFromString(String string, String type)
      throws JsonProcessingException {
    return objectMapper.readValue(string, typeMap.get(type));
  }
}
