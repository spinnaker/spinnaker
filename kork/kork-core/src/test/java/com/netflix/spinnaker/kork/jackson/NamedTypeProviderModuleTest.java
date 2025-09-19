/*
 * Copyright 2024 Apple, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.jackson;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.json.AutoConfigureJson;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;

@SpringBootTest(classes = NamedTypeProviderModuleTest.TestConfig.class)
@AutoConfigureJson
@ImportAutoConfiguration(NamedTypeAutoConfiguration.class)
class NamedTypeProviderModuleTest {
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
  interface BaseType {}

  @Data
  @JsonTypeName
  static class FirstType implements BaseType {
    private String alef;
  }

  @Data
  @JsonTypeName
  static class SecondType implements BaseType {
    private String bet;
  }

  static class TestConfig {
    @Bean
    NamedTypeProvider testNamedTypeProvider() {
      return () -> List.of(new NamedType(FirstType.class), new NamedType(SecondType.class));
    }
  }

  @Autowired ObjectMapper objectMapper;

  @Test
  void objectMapperHasNamedSubtypesRegistered() throws JsonProcessingException {
    var firstType = new FirstType();
    firstType.setAlef("knee");
    assertEquals(firstType, serializeRoundTrip(firstType));
    var secondType = new SecondType();
    secondType.setBet("shin");
    assertEquals(secondType, serializeRoundTrip(secondType));
  }

  private BaseType serializeRoundTrip(BaseType baseType) throws JsonProcessingException {
    return objectMapper.readValue(objectMapper.writeValueAsString(baseType), BaseType.class);
  }
}
