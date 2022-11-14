/*
 * Copyright 2022 Apple Inc.
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

package com.netflix.spinnaker.credentials.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SensitiveSerializerTest {
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addSerializer(String.class, new SensitiveSerializer(new SensitiveProperties()));
    mapper.registerModule(module);
  }

  @Test
  void masksExplicitSensitiveFields() throws JsonProcessingException {
    SensitiveAccount account =
        SensitiveAccount.builder().name("alfred").username("fred").password("hunter2").build();
    assertThat(mapper.writeValueAsString(account))
        .contains(account.getName(), account.getUsername())
        .doesNotContain(account.getPassword());
  }

  @Test
  void masksImplicitSensitiveFieldsInCredentialsDefinition() throws JsonProcessingException {
    SensitiveAccount account =
        SensitiveAccount.builder()
            .name("betty")
            .username("bet")
            .token(UUID.randomUUID().toString())
            .build();
    assertThat(mapper.writeValueAsString(account))
        .contains(account.getName(), account.getUsername())
        .doesNotContain(account.getToken());
  }

  @Test
  void doesNotMaskSecretReferences() throws JsonProcessingException {
    SensitiveAccount account =
        SensitiveAccount.builder()
            .name("charlie")
            .username("chancery")
            .password("secret://chest?k=gold")
            .build();
    assertThat(mapper.writeValueAsString(account))
        .contains(account.getName(), account.getUsername(), account.getPassword());
  }
}
