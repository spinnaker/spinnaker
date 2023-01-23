/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.kayenta;

import com.netflix.kayenta.config.ApplicationConfiguration;
import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Import(ApplicationConfiguration.class)
@Configuration
public class Main {
  private static final Map<String, Object> DEFAULT_PROPS =
      new DefaultPropertiesBuilder()
          .property("spring.application.name", "kayenta")
          .property("spring.jackson.serialization.WRITE_DATES_AS_TIMESTAMPS", "false")
          .property("spring.jackson.default-property-inclusion", "non_null")
          .build();

  public static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main.class).run(args);
  }
}
