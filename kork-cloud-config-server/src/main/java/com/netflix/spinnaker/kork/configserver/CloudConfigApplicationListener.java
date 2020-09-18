/*
 * Copyright 2019 Pivotal, Inc.
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
 *
 */

package com.netflix.spinnaker.kork.configserver;

import com.netflix.spinnaker.kork.secrets.SecretAwarePropertySource;
import org.springframework.boot.context.event.ApplicationPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

public class CloudConfigApplicationListener
    implements ApplicationListener<ApplicationPreparedEvent> {
  private static final String APPLICATION_CONFIG_PROPERTY_SOURCE_PREFIX = "applicationConfig:";
  private static final String CONFIG_SERVER_PROPERTY_SOURCE_NAME = "bootstrapProperties";

  @Override
  public void onApplicationEvent(ApplicationPreparedEvent event) {
    ConfigurableApplicationContext context = event.getApplicationContext();
    ConfigurableEnvironment environment = context.getEnvironment();

    for (PropertySource propertySource : environment.getPropertySources()) {
      if (shouldWrap(propertySource)) {
        CloudConfigAwarePropertySource wrapper =
            new CloudConfigAwarePropertySource(propertySource, context);
        environment.getPropertySources().replace(propertySource.getName(), wrapper);
      }
    }
  }

  private boolean shouldWrap(PropertySource propertySource) {
    return (propertySource.getName().startsWith(APPLICATION_CONFIG_PROPERTY_SOURCE_PREFIX)
            || propertySource.getName().equals(CONFIG_SERVER_PROPERTY_SOURCE_NAME))
        && !((propertySource instanceof CloudConfigAwarePropertySource)
            || (propertySource instanceof SecretAwarePropertySource));
  }
}
