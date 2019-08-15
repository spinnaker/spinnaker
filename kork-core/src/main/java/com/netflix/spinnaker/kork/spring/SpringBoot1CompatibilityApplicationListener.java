/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.spring;

import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;

/** Makes Spring Boot 2 behave a bit more like Spring 1.x */
public class SpringBoot1CompatibilityApplicationListener
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {
  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    // Allow extensions to override bean definitions in OSS projects.
    setIfMissing("spring.main.allow-bean-definition-overriding", "true");

    // Put spring endpoints on / instead of /actuator (for /health backwards compat).
    setIfMissing("management.endpoints.web.base-path", "/");
  }

  private void setIfMissing(String property, String value) {
    if (System.getProperty(property) == null) {
      System.setProperty(property, value);
    }
  }
}
