/*
 * Copyright 2019 Pivotal, Inc.
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

package com.netflix.spinnaker.kork.configserver;

public class ConfigServerBootstrap {
  public static void systemProperties(String applicationName) {
    defaultSystemProperty("spring.application.name", applicationName);
    // default locations must be included pending the resolution
    // of https://github.com/spring-cloud/spring-cloud-commons/issues/466
    defaultSystemProperty(
        "spring.cloud.bootstrap.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/,optional:/opt/spinnaker/config/,optional:${user.home}/.spinnaker/");
    defaultSystemProperty(
        "spring.cloud.bootstrap.name", "spinnakerconfig,${spring.application.name}config");
    defaultSystemProperty("spring.cloud.config.server.bootstrap", "true");
    defaultSystemProperty("spring.cloud.bootstrap.enabled", "true");
  }

  private static void defaultSystemProperty(String key, String value) {
    if (System.getProperty(key) == null) {
      System.setProperty(key, value);
    }
  }
}
