/*
 * Copyright 2017 Google, Inc.
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

package com.netflix.spinnaker.halyard.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import lombok.Data;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * This is the collection of general, top-level flags that come from the application configuration
 * for halyard. This class is both @AutoWired'able and can act as a singleton via the getInstance()
 * function. This is so the same class may be reused between halyard's CLI and daemon.
 */
@Data
@Configuration
@PropertySource(
    value = "file:" + GlobalApplicationOptions.CONFIG_PATH,
    ignoreResourceNotFound = true)
public class GlobalApplicationOptions {

  static final String CONFIG_PATH = "/opt/spinnaker/hal.properties";

  @Value("${use-remote-daemon:false}")
  private boolean useRemoteDaemon = false;

  public boolean isUseRemoteDaemon() {
    return options.useRemoteDaemon;
  }

  public static GlobalApplicationOptions getInstance() {
    if (GlobalApplicationOptions.options == null) {
      Yaml yamlParser = new Yaml(new SafeConstructor());
      ObjectMapper objectMapper = new ObjectMapper();

      objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
      objectMapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, false);
      objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

      try {
        GlobalApplicationOptions.options =
            objectMapper.convertValue(
                yamlParser.load(FileUtils.openInputStream(new File(CONFIG_PATH))),
                GlobalApplicationOptions.class);
      } catch (IOException e) {
        GlobalApplicationOptions.options = new GlobalApplicationOptions();
      }
    }
    return GlobalApplicationOptions.options;
  }

  private static GlobalApplicationOptions options = null;
}
