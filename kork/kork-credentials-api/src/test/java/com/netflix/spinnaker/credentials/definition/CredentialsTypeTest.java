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

package com.netflix.spinnaker.credentials.definition;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.kork.jackson.NamedTypeParser;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.NamedType;

class CredentialsTypeTest {
  @Test
  void typeDiscriminatorUsage() throws JacksonException {
    var mapper =
        JsonMapper.builder()
            .addMixIn(CredentialsDefinition.class, CredentialsDefinitionMixin.class)
            .build();
    new ObjectMapperSubtypeConfigurer(new CredentialsTypeParser())
        .registerSubtype(
            mapper,
            new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
                CredentialsDefinition.class, List.of(getClass().getPackageName())));

    CredentialsDefinition definition =
        mapper.readValue("{\"type\":\"test\",\"name\":\"success\"}", CredentialsDefinition.class);
    assertThat(definition).isInstanceOf(TestCredentialsDefinition.class);
    assertThat(definition.getName()).isEqualTo("success");
  }

  static class CredentialsTypeParser implements NamedTypeParser {
    @Nullable
    @Override
    public NamedType parse(@Nonnull Class<?> type) {
      CredentialsType annotation = type.getAnnotation(CredentialsType.class);
      if (annotation == null || annotation.value().isEmpty()) {
        return null;
      }
      return new NamedType(type, annotation.value());
    }
  }
}
