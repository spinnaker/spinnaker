/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.config.config.v1;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.nio.file.Paths;

@Component
public class ResourceConfig {
  /**
   * Directory containing the halconfig.
   *
   * @param path Defaults to "~/.hal".
   * @return The path with home (~) expanded.
   */
  @Bean
  String halconfigDirectory(@Value("${halyard.halconfig.directory:~/.hal}") String path) {
    return normalizePath(path);
  }

  @Bean
  String halconfigPath(@Value("${halyard.halconfig.directory:~/.hal}") String path) {
    return normalizePath(Paths.get(path, "config").toString());
  }
  
  /**
   * Version of halyard.
   *
   * This is useful for implementing breaking version changes in Spinnaker that need to be migrated by some tool
   * (in this case Halyard).
   *
   * @return the version of halyard.
   */
  @Bean
  String halyardVersion() {
    return getClass().getPackage().getImplementationVersion();
  }

  @Bean
  String spinconfigBucket(@Value("${spinnaker.config.input.bucket:halconfig}") String spinconfigBucket) {
    return spinconfigBucket;
  }

  @Bean
  String spinnakerStagingDependencyPath(@Value("${spinnaker.config.staging.directory:~/.halyard}") String path) {
    return Paths.get(normalizePath(path), "dependency").toString();
  }

  @Bean
  Yaml yamlParser() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    return new Yaml(options);
  }

  private String normalizePath(String path) {
    String result = path.replaceFirst("^~", System.getProperty("user.home"));
    // Strip trailing path separator
    if (result.endsWith(File.separator)) {
      result = result.substring(0, result.length() - 1);
    }

    return result;
  }
}
