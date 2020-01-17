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

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import lombok.SneakyThrows;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Root-level configuration properties for plugins.
 *
 * @see PluginsAutoConfiguration
 */
@ConfigurationProperties(PluginsConfigurationProperties.CONFIG_NAMESPACE)
public class PluginsConfigurationProperties {
  public static final String CONFIG_NAMESPACE = "spinnaker.extensibility";
  public static final String DEFAULT_ROOT_PATH = "plugins";
  public static final String FRONT5O_REPOSITORY = "front50";

  /**
   * The root filepath to the directory containing all plugins.
   *
   * <p>If an absolute path is not provided, the path will be calculated relative to the executable.
   */
  // Note that this property is not bound at PluginManager initialization time,
  // but is retained here for documentation purposes. Later consumers of this property
  // will see the correctly bound value that was used when initializing the plugin subsystem.
  private String pluginsRootPath = DEFAULT_ROOT_PATH;

  // If for some reason we change the associated property name ensure this constant
  // is updated to match. This is the actual value we will read from the environment
  // at init time.
  public static final String ROOT_PATH_CONFIG = CONFIG_NAMESPACE + ".plugins-root-path";

  public String getPluginsRootPath() {
    return pluginsRootPath;
  }

  public void setPluginsRootPath(String pluginsRootPath) {
    this.pluginsRootPath = pluginsRootPath;
  }

  /**
   * A definition of repositories for use in plugin downloads.
   *
   * <p>The key of this map is the name of the repository.
   */
  public Map<String, PluginRepositoryProperties> repositories = new HashMap<>();

  /** Definition of a single {@link org.pf4j.update.UpdateRepository}. */
  public static class PluginRepositoryProperties {
    /** Flag to determine if repository is enabled. */
    private boolean enabled = true;

    /** The base URL to the repository. */
    private String url;

    /** Configuration for an optional override of {@link org.pf4j.update.FileDownloader}. */
    public FileDownloaderProperties fileDownloader;

    /** Custom {@link org.pf4j.update.FileDownloader} configuration. */
    public static class FileDownloaderProperties {
      /** The fully qualified class name of the FileDownloader to use. */
      public String className;

      /**
       * The configuration for the FileDownloader.
       *
       * <p>If defined, the FileDownloader must use the {@link
       * com.netflix.spinnaker.kork.plugins.config.Configurable} annotation to inform the plugin
       * framework how to cast the configuration for injection.
       */
      public Object config;
    }

    @SneakyThrows
    public URL getUrl() {
      return new URL(url);
    }

    public boolean isEnabled() {
      return enabled;
    }
  }
}
