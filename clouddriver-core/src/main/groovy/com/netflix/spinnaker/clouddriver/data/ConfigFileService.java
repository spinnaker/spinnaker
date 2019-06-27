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

package com.netflix.spinnaker.clouddriver.data;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.NoSuchResourceException;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.cloud.config.server.support.EnvironmentPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;

@Slf4j
public class ConfigFileService {
  private static final String CONFIG_SERVER_RESOURCE_PREFIX = "configserver:";
  private static final String CLASSPATH_FILE_PREFIX = "classpath:";

  private final ResourceRepository resourceRepository;
  private final EnvironmentRepository environmentRepository;

  @Value("${spring.application.name:application}")
  private String applicationName = "application";

  public ConfigFileService() {
    resourceRepository = null;
    environmentRepository = null;
  }

  public ConfigFileService(
      ResourceRepository resourceRepository, EnvironmentRepository environmentRepository) {
    this.resourceRepository = resourceRepository;
    this.environmentRepository = environmentRepository;
  }

  public String getLocalPath(String path, String tempFilePrefix, String tempFileSuffix) {
    if (StringUtils.isNotEmpty(path)) {
      if (isCloudConfigResource(path)) {
        String configServerContents = retrieveFromConfigServer(path);
        return writeToTempFile(configServerContents, tempFilePrefix, tempFileSuffix);
      } else {
        return verifyLocalPath(path);
      }
    }

    return null;
  }

  public String getLocalPathForContents(
      String contents, String tempFilePrefix, String tempFileSuffix) {
    if (StringUtils.isNotEmpty(contents)) {
      return writeToTempFile(contents, tempFilePrefix, tempFileSuffix);
    }

    return null;
  }

  public String getContents(String path) {
    if (StringUtils.isNotEmpty(path)) {
      if (isCloudConfigResource(path)) {
        return retrieveFromConfigServer(path);
      }

      if (isClasspathResource(path)) {
        return retrieveFromClasspath(path);
      }

      return retrieveFromLocalPath(path);
    }

    return null;
  }

  private boolean isCloudConfigResource(String path) {
    return path.startsWith(CONFIG_SERVER_RESOURCE_PREFIX);
  }

  private boolean isClasspathResource(String path) {
    return path.startsWith(CLASSPATH_FILE_PREFIX);
  }

  private String verifyLocalPath(String path) {
    if (Files.isReadable(Paths.get(path))) {
      return path;
    } else {
      throw new RuntimeException("File \"" + path + "\" not found or is not readable");
    }
  }

  private String retrieveFromLocalPath(String path) {
    try {
      Path filePath = Paths.get(path);
      return new String(Files.readAllBytes(filePath));
    } catch (FileNotFoundException e) {
      throw new RuntimeException("File \"" + path + "\" not found or is not readable", e);
    } catch (IOException e) {
      throw new RuntimeException("Exception reading file " + path, e);
    }
  }

  private String retrieveFromClasspath(String path) {
    try {
      String filePath = path.substring(CLASSPATH_FILE_PREFIX.length());
      InputStream inputStream = getClass().getResourceAsStream(filePath);
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new RuntimeException("Exception reading classpath resource \"" + path + "\"", e);
    }
  }

  private String retrieveFromConfigServer(String path) {
    if (resourceRepository == null || environmentRepository == null) {
      throw new RuntimeException(
          "Config Server repository not configured for resource \"" + path + "\"");
    }

    try {
      String fileName = path.substring(CONFIG_SERVER_RESOURCE_PREFIX.length());
      Resource resource = this.resourceRepository.findOne(applicationName, null, null, fileName);
      try (InputStream inputStream = resource.getInputStream()) {
        Environment environment = this.environmentRepository.findOne(applicationName, null, null);

        String text = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        StandardEnvironment preparedEnvironment =
            EnvironmentPropertySource.prepareEnvironment(environment);
        return EnvironmentPropertySource.resolvePlaceholders(preparedEnvironment, text);
      }
    } catch (NoSuchResourceException e) {
      throw new RuntimeException("The resource \"" + path + "\" was not found in config server", e);
    } catch (IOException e) {
      throw new RuntimeException("Exception reading config server resource \"" + path + "\"", e);
    }
  }

  private String writeToTempFile(String contents, String tempFilePrefix, String tempFileSuffix) {
    try {
      File tempFile = File.createTempFile(tempFilePrefix, tempFileSuffix);
      tempFile.deleteOnExit();

      BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile));
      writer.write(contents);
      writer.close();

      log.info(
          "Configuration for {} written to temporary file {}",
          tempFilePrefix,
          tempFile.getAbsolutePath());

      return tempFile.getAbsolutePath();
    } catch (IOException e) {
      throw new RuntimeException(
          "Exception writing temporary file with prefix \""
              + tempFilePrefix
              + "\": "
              + e.getMessage(),
          e);
    }
  }
}
