/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard;

import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder;
import com.netflix.spinnaker.kork.configserver.ConfigServerBootstrap;
import java.util.Map;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ComponentScan(value = {"com.netflix.spinnaker.halyard"})
@EnableAutoConfiguration
@EnableConfigServer
public class Main extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS = new DefaultPropertiesBuilder().build();

  public static void main(String... args) {
    ConfigServerBootstrap.systemProperties("halyard");
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Main.class).run(args);
  }

  @Override
  protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
    return application.properties(DEFAULT_PROPS).sources(Main.class);
  }
}
