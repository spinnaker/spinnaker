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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.NoSuchResourceException;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.support.EnvironmentPropertySource;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;

public class CloudConfigResourceService implements EnvironmentAware {
  private static final String CONFIG_SERVER_RESOURCE_PREFIX = "configserver:";

  private final ResourceRepository resourceRepository;
  private final EnvironmentRepository environmentRepository;

  @Value("${spring.application.name:application}")
  private String applicationName = "application";

  private String profiles;

  public CloudConfigResourceService() {
    this.resourceRepository = null;
    this.environmentRepository = null;
  }

  public CloudConfigResourceService(
      ResourceRepository resourceRepository, EnvironmentRepository environmentRepository) {
    this.resourceRepository = resourceRepository;
    this.environmentRepository = environmentRepository;
  }

  public String getLocalPath(String path) {
    String contents = retrieveFromConfigServer(path);
    return ConfigFileUtils.writeToTempFile(contents, getResourceName(path));
  }

  private String retrieveFromConfigServer(String path) {
    if (resourceRepository == null || environmentRepository == null) {
      throw new ConfigFileLoadingException(
          "Config Server repository not configured for resource \"" + path + "\"");
    }

    try {
      String fileName = getResourceName(path);
      Resource resource =
          this.resourceRepository.findOne(applicationName, profiles, null, fileName);
      try (InputStream inputStream = resource.getInputStream()) {
        Environment environment =
            this.environmentRepository.findOne(applicationName, profiles, null);

        String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        StandardEnvironment preparedEnvironment =
            EnvironmentPropertySource.prepareEnvironment(environment);
        return EnvironmentPropertySource.resolvePlaceholders(preparedEnvironment, text);
      }
    } catch (NoSuchResourceException e) {
      throw new ConfigFileLoadingException(
          "The resource \"" + path + "\" was not found in config server", e);
    } catch (IOException e) {
      throw new ConfigFileLoadingException(
          "Exception reading config server resource \"" + path + "\"", e);
    }
  }

  private String getResourceName(String path) {
    return path.substring(CONFIG_SERVER_RESOURCE_PREFIX.length());
  }

  @Override
  public void setEnvironment(org.springframework.core.env.Environment environment) {
    profiles = StringUtils.join(environment.getActiveProfiles(), ",");
  }

  public static boolean isCloudConfigResource(String path) {
    return path.startsWith(CONFIG_SERVER_RESOURCE_PREFIX);
  }
}
