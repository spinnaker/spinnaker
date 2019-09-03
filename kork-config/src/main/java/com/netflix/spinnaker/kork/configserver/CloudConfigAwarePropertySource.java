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
 */

package com.netflix.spinnaker.kork.configserver;

import org.springframework.beans.BeansException;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

public class CloudConfigAwarePropertySource extends EnumerablePropertySource<PropertySource> {
  private final ConfigurableApplicationContext context;
  private CloudConfigResourceService resourceService;

  CloudConfigAwarePropertySource(PropertySource source, ConfigurableApplicationContext context) {
    super(source.getName(), source);
    this.context = context;
  }

  @Override
  public Object getProperty(String name) {
    Object value = source.getProperty(name);
    if (value instanceof String) {
      String stringValue = (String) value;
      if (CloudConfigResourceService.isCloudConfigResource(stringValue)) {
        resolveResourceService(stringValue);
        value = resourceService.getLocalPath(stringValue);
      }
    }
    return value;
  }

  private void resolveResourceService(String path) {
    if (resourceService == null) {
      try {
        resourceService = context.getBean(CloudConfigResourceService.class);
      } catch (BeansException e) {
        throw new ConfigFileLoadingException(
            "Config Server repository not configured for resource \"" + path + "\"");
      }
    }
  }

  @Override
  public String[] getPropertyNames() {
    if (source instanceof EnumerablePropertySource) {
      return ((EnumerablePropertySource) source).getPropertyNames();
    } else {
      return new String[0];
    }
  }
}
