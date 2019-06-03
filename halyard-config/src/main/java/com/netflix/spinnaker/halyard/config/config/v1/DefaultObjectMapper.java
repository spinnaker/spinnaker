/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.halyard.config.config.v1;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * An ObjectMapper replacing the now private `objectMapper` bean from
 * RepositoryRestMvcConfiguration.
 */
@Component
@Primary
public class DefaultObjectMapper extends ObjectMapper {
  DefaultObjectMapper() {
    super();
    this.configure(SerializationFeature.INDENT_OUTPUT, true);
    this.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    Jdk8Module jdk8Module = new Jdk8Module();
    jdk8Module.configureAbsentsAsNulls(true);
    this.registerModule(jdk8Module);
  }
}
