/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.jackson;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS;
import static com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

public class OrcaObjectMapper {
  private OrcaObjectMapper() {}

  private static final ObjectMapper INSTANCE = newInstance();

  public static ObjectMapper newInstance() {
    ObjectMapper instance = new ObjectMapper();
    instance.registerModule(new Jdk8Module());
    instance.registerModule(new GuavaModule());
    instance.registerModule(new JavaTimeModule());
    instance.registerModule(new KotlinModule());
    instance.disable(READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    instance.disable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    instance.disable(FAIL_ON_UNKNOWN_PROPERTIES);
    instance.setSerializationInclusion(NON_NULL);
    return instance;
  }

  /**
   * Return an ObjectMapper instance that can be reused. Do not change the configuration of this
   * instance as it will be shared across the entire application, use {@link #newInstance()}
   * instead.
   *
   * @return Reusable ObjectMapper instance
   */
  public static ObjectMapper getInstance() {
    return INSTANCE;
  }
}
