/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.configserver;

import com.netflix.spinnaker.kork.boot.DefaultPropertiesBuilder;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

@SpringBootApplication
@EnableConfigServer
public class ConfigServerTestApplication {
  public static final Map<String, Object> DEFAULT_PROPS_CLOUD =
      new DefaultPropertiesBuilder()
          .property("server.port", "0")
          .build(); // For tomcat to pick randomly available port

  public static void execute(String dummyUserHome) {
    System.setProperty("user.home", dummyUserHome);
    ConfigServerBootstrap.systemProperties("kork");
    SpringApplication app = new SpringApplication(ConfigServerTestApplication.class);
    app.setDefaultProperties(DEFAULT_PROPS_CLOUD);
    app.run();
  }
}
