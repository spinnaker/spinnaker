/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.jackson;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator;
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.StringSubtypeLocator;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class ObjectMapperSubtypeConfigurerTest {

  ObjectMapper mapper;

  @Before
  public void setup() {
    mapper = new ObjectMapper();
  }

  @Test
  public void shouldRegisterSubtypesByClass() throws JsonProcessingException {
    new ObjectMapperSubtypeConfigurer(true)
        .registerSubtype(mapper, new ClassSubtypeLocator(RootType.class, searchPackages()));

    assertEquals("{\"kind\":\"child\"}", mapper.writeValueAsString(new ChildType()));
  }

  @Test
  public void shouldRegisterSubtypesByName() throws JsonProcessingException {
    new ObjectMapperSubtypeConfigurer(true)
        .registerSubtype(
            mapper,
            new StringSubtypeLocator(
                "com.netflix.spinnaker.kork.jackson.RootType", searchPackages()));

    assertEquals("{\"kind\":\"child\"}", mapper.writeValueAsString(new ChildType()));
  }

  @Test(expected = InvalidSubtypeConfigurationException.class)
  public void shouldThrowWhenSubtypeNameIsUndefined() {
    new ObjectMapperSubtypeConfigurer(true)
        .registerSubtype(
            mapper, new ClassSubtypeLocator(UndefinedRootType.class, searchPackages()));
  }

  List<String> searchPackages() {
    List<String> searchPackages = new ArrayList<>();
    searchPackages.add("com.netflix.spinnaker.kork.jackson");
    return searchPackages;
  }
}

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "kind")
abstract class RootType {}

@JsonTypeName("child")
class ChildType extends RootType {}

@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "kind")
class UndefinedRootType {}

class UndefinedType extends UndefinedRootType {}
