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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ConfigFileService {
  private static final String CLASSPATH_FILE_PREFIX = "classpath:";
  private final CloudConfigResourceService cloudConfigResourceService;

  public ConfigFileService(CloudConfigResourceService cloudConfigResourceService) {
    this.cloudConfigResourceService = cloudConfigResourceService;
  }

  public String getLocalPath(String path) {
    if (CloudConfigResourceService.isCloudConfigResource(path)
        && cloudConfigResourceService != null) {
      path = cloudConfigResourceService.getLocalPath(path);
    }
    verifyLocalPath(path);
    return path;
  }

  public String getLocalPathForContents(String contents, String path) {
    if (StringUtils.isNotEmpty(contents)) {
      return ConfigFileUtils.writeToTempFile(contents, path);
    }

    return null;
  }

  public String getContents(String path) {
    if (StringUtils.isNotEmpty(path)) {
      if (isClasspathResource(path)) {
        return retrieveFromClasspath(path);
      }
      return retrieveFromLocalPath(getLocalPath(path));
    }

    return null;
  }

  private boolean isClasspathResource(String path) {
    return path.startsWith(CLASSPATH_FILE_PREFIX);
  }

  private void verifyLocalPath(String path) {
    if (StringUtils.isNotEmpty(path) && !Files.isReadable(Paths.get(path))) {
      throw new ConfigFileLoadingException("File \"" + path + "\" not found or is not readable");
    }
  }

  private String retrieveFromLocalPath(String path) {
    try {
      Path filePath = Paths.get(path);
      return new String(Files.readAllBytes(filePath));
    } catch (FileNotFoundException e) {
      throw new ConfigFileLoadingException("File \"" + path + "\" not found or is not readable", e);
    } catch (IOException e) {
      throw new ConfigFileLoadingException("Exception reading file " + path, e);
    }
  }

  private String retrieveFromClasspath(String path) {
    try {
      String filePath = path.substring(ConfigFileService.CLASSPATH_FILE_PREFIX.length());
      InputStream inputStream = getClass().getResourceAsStream(filePath);
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new ConfigFileLoadingException(
          "Exception reading classpath resource \"" + path + "\"", e);
    }
  }
}
