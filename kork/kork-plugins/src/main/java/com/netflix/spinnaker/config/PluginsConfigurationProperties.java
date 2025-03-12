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
import javax.annotation.Nullable;
import lombok.SneakyThrows;

/**
 * Root-level configuration properties for plugins.
 *
 * <p>These properties are mapped using a {@link
 * org.springframework.boot.context.properties.bind.Binder} because they are needed before the
 * {@link org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor}
 * has run.
 *
 * @see PluginsAutoConfiguration
 */
public class PluginsConfigurationProperties {
  public static final String CONFIG_NAMESPACE = "spinnaker.extensibility";
  public static final String DEFAULT_ROOT_PATH = "plugins";
  public static final String FRONT5O_REPOSITORY = "front50";
  public static final String SPINNAKER_OFFICIAL_REPOSITORY = "spinnaker-official";
  public static final String SPINNAKER_COMMUNITY_REPOSITORY = "spinnaker-community";

  /**
   * The root filepath to the directory containing all plugins.
   *
   * <p>If an absolute path is not provided, the path will be calculated relative to the executable.
   */
  private String pluginsRootPath = DEFAULT_ROOT_PATH;

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

  /**
   * Whether or not to add the plugin repositories from https://github.com/spinnaker/plugins by
   * default.
   */
  private boolean enableDefaultRepositories = true;

  public boolean isEnableDefaultRepositories() {
    return enableDefaultRepositories;
  }

  public void setEnableDefaultRepositories(boolean enableDefaultRepositories) {
    this.enableDefaultRepositories = enableDefaultRepositories;
  }

  /** Definition of a single {@link org.pf4j.update.UpdateRepository}. */
  public static class PluginRepositoryProperties {
    /** Flag to determine if repository is enabled. */
    private boolean enabled = true;

    /** The base URL to the repository. */
    private String url;

    /** Configuration for an optional override of {@link org.pf4j.update.FileDownloader}. */
    @Nullable public FileDownloaderProperties fileDownloader;

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
