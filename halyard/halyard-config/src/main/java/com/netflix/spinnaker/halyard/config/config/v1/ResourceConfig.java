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

import java.io.File;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.representer.Representer;

@Component
public class ResourceConfig {

  public static final String DEFAULT_HALCONFIG_BUCKET = "halconfig";

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
  TaskScheduler taskScheduler() {
    return new ConcurrentTaskScheduler(Executors.newSingleThreadScheduledExecutor());
  }

  @Bean
  String localBomPath(@Value("${halyard.halconfig.directory:~/.hal}") String path) {

    return normalizePath(Paths.get(path, ".boms").toString());
  }

  /**
   * Version of halyard.
   *
   * <p>This is useful for implementing breaking version changes in Spinnaker that need to be
   * migrated by some tool (in this case Halyard).
   *
   * @return the version of halyard.
   */
  @Bean
  String halyardVersion() {
    return Optional.ofNullable(getClass().getPackage().getImplementationVersion())
        .orElse("Unknown");
  }

  @Bean
  String spinconfigBucket(
      @Value("${spinnaker.config.input.bucket:halconfig}") String spinconfigBucket) {
    return spinconfigBucket;
  }

  @Bean
  Boolean gcsEnabled(@Value("${spinnaker.config.input.gcs.enabled:true}") boolean gcsEnabled) {
    return gcsEnabled;
  }

  @Bean
  String gitRoot(@Value("${git.root:~/dev/spinnaker}") String gitRoot) {
    return normalizePath(gitRoot);
  }

  @Bean
  String spinnakerStagingDependencyPath(
      @Value("${spinnaker.config.staging.directory:~/.halyard}") String path) {
    return Paths.get(normalizePath(path), "dependency").toString();
  }

  @Bean
  @Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE) // snake yaml is not thread safe
  // (https://bitbucket.org/asomov/snakeyaml/wiki/Documentation#markdown-header-threading)
  Yaml yamlParser() {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setDefaultScalarStyle(DumperOptions.ScalarStyle.PLAIN);
    return new Yaml(new SafeConstructor(), new Representer(), options);
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
