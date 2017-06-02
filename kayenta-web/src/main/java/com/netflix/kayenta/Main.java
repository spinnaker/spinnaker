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

import com.netflix.kayenta.atlas.config.AtlasConfiguration;
import com.netflix.kayenta.config.KayentaConfiguration;
import com.netflix.kayenta.config.WebConfiguration;
import com.netflix.kayenta.gcs.config.GcsConfiguration;
import com.netflix.kayenta.google.config.GoogleConfiguration;
import com.netflix.kayenta.memory.config.MemoryConfiguration;
import com.netflix.kayenta.stackdriver.config.StackdriverConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.web.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Configuration
@Import({
  AtlasConfiguration.class,
  GcsConfiguration.class,
  GoogleConfiguration.class,
  KayentaConfiguration.class,
  MemoryConfiguration.class,
  StackdriverConfiguration.class,
  WebConfiguration.class
})
@ComponentScan({
  "com.netflix.spinnaker.config",
})
@EnableAutoConfiguration
class Main extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = buildDefaults();

  private static Map<String, Object> buildDefaults() {
    Map<String, String> defaults = new HashMap<>();
    defaults.put("netflix.environment", "test");
    defaults.put("netflix.account", "${netflix.environment}");
    defaults.put("netflix.stack", "test");
    defaults.put("spring.config.location", "${user.home}/.kayenta/");
    defaults.put("spring.application.name", "kayenta");
    defaults.put("spring.config.name", "kayenta,${spring.application.name}");
    defaults.put("spring.profiles.active", "${netflix.environment},local");
    return Collections.unmodifiableMap(defaults);
  }

  public static void main(String... args) {
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main.class).run(args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.properties(DEFAULT_PROPS).sources(Main.class);
  }
}

