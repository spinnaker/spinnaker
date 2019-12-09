/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root-level configuration properties for plugins.
 *
 * @see PluginsAutoConfiguration
 */
@ConfigurationProperties(PluginsConfigurationProperties.CONFIG_NAMESPACE)
public class PluginsConfigurationProperties {
  public static final String CONFIG_NAMESPACE = "spinnaker.plugins";
  public static final String DEFAULT_ROOT_PATH = "plugins";

  /**
   * The root filepath to the directory containing all plugins.
   *
   * <p>If an absolute path is not provided, the path will be calculated relative to the executable.
   */
  // Note that this property is not bound at PluginManager initialization time,
  // but is retained here for documentation purposes. Later consumers of this property
  // will see the correctly bound value that was used when initializing the plugin subsystem.
  private String rootPath = DEFAULT_ROOT_PATH;

  // If for some reason we change the associated property name ensure this constant
  // is updated to match. This is the actual value we will read from the environment
  // at init time.
  public static final String ROOT_PATH_CONFIG = CONFIG_NAMESPACE + ".root-path";

  public String getRootPath() {
    return rootPath;
  }

  public void setRootPath(String rootPath) {
    this.rootPath = rootPath;
  }
}
