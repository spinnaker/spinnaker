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

import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder;
import com.netflix.spinnaker.kork.configserver.ConfigServerBootstrap;
import java.util.Map;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Application entry point. */
@EnableScheduling
@ComponentScan({"com.netflix.spinnaker.echo.config", "com.netflix.spinnaker.config"})
@SpringBootApplication(
    scanBasePackages = {"com.netflix.spinnaker.echo.config", "com.netflix.spinnaker.config"},
    exclude = {DataSourceAutoConfiguration.class, GsonAutoConfiguration.class})
public class Application extends SpringBootServletInitializer {
  private static final Map<String, Object> DEFAULT_PROPS =
      new DefaultPropertiesBuilder().property("spring.application.name", "echo").build();

  public static void main(String... args) {
    ConfigServerBootstrap.systemProperties("echo");
    System.setProperty("spring.main.allow-bean-definition-overriding", "true");
    new SpringApplicationBuilder().properties(DEFAULT_PROPS).sources(Application.class).run(args);
  }

  @Override
  public SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
    return builder.properties(DEFAULT_PROPS).sources(Application.class);
  }
}
