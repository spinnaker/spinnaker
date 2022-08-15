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

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NamedTypeParserTest {
  @Test
  void customNamedTypeDiscriminator() throws JsonProcessingException {
    var mapper = new ObjectMapper();
    NamedTypeParser parser =
        type ->
            Optional.ofNullable(type.getAnnotation(TypeDiscriminator.class))
                .map(TypeDiscriminator::value)
                .map(name -> new NamedType(type, name))
                .orElse(null);

    new ObjectMapperSubtypeConfigurer(parser)
        .registerSubtype(
            mapper,
            new ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(
                UncleType.class, List.of("com.netflix.spinnaker.kork.jackson")));

    assertEquals("{\"kind\":\"niece\"}", mapper.writeValueAsString(new NieceType()));
  }
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface TypeDiscriminator {
  String value();
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
abstract class UncleType {}

@TypeDiscriminator("niece")
class NieceType extends UncleType {}
