/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Application entry point.
 */
@Configuration
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, GsonAutoConfiguration.class})
@ComponentScan({
  "com.netflix.spinnaker.echo.config",
  "com.netflix.spinnaker.config"
})
public class Application extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = buildDefaults();

  private static Map<String, Object> buildDefaults() {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("netflix.environment", "test");
    defaults.put("netflix.account", "${netflix.environment}");
    defaults.put("netflix.stack", "test");
    defaults.put("spring.config.location", "${user.home}/.spinnaker/");
    defaults.put("spring.application.name", "echo");
    defaults.put("spring.config.name", "spinnaker,${spring.application.name}");
    defaults.put("spring.profiles.active", "${netflix.environment},local");
    return Collections.unmodifiableMap(defaults);
  }

  public static void main(String... args) {
    System.setProperty("spring.main.allow-bean-definition-overriding", "true");
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Application.class).run(args);
  }

  @Override
  public SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.properties(DEFAULT_PROPS).sources(Application.class);
  }
}
