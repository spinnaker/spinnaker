/*
 * Copyright 2019 Armory, Inc.
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
package com.netflix.spinnaker.kork.plugins.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.common.annotations.Beta;
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginConfiguration;
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginProperties;
import com.netflix.spinnaker.kork.plugins.spring.configs.PluginPropertyDetails;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * PluginLoader is used by SpinnakerApplication in order to enable plugins. Plugins are loaded by
 * adding their jar locations to the classpath. If loadPlugins is called prior to spring context
 * initialization, plugins are able to be autowired. The PluginLoader config file's location is
 * configurable itself, via an environment variable.
 */
@Beta
public class PluginLoader {

  static final String DEFAULT_PLUGIN_CONFIG_PATH = "/opt/spinnaker/config/plugins.yml";
  private static final Logger log = LoggerFactory.getLogger(PluginLoader.class);

  private String filename;

  PluginLoader(String filename) {
    this.filename = filename;
  }

  PluginLoader() {
    this(
        Optional.ofNullable(System.getenv("PLUGIN_CONFIG_LOCATION"))
            .orElse(PluginLoader.DEFAULT_PLUGIN_CONFIG_PATH));
  }

  /**
   * This is the entry point for loading plugins. This method parses a config file and adds plugin
   * jar paths to the classpath in order for plugins to load.
   *
   * @param source The parent class loader for delegation
   */
  public void loadPlugins(Class source) {
    URL[] urls;
    if (checkFileExists() == false) {
      log.info("Not loading plugins: No plugin configuration file found: {}", this.filename);
      return;
    }
    try (InputStream inputStream = openFromFile()) {
      PluginPropertyDetails props = parsePluginConfigs(inputStream);
      List<PluginConfiguration> pluginConfigurations = getEnabledJars(props);
      logPlugins(pluginConfigurations);
      urls = getJarPathsFromPluginConfigurations(pluginConfigurations, props.downloadingEnabled);
    } catch (IOException e) {
      throw new MissingPluginConfigurationException(
          String.format(
              "Not loading plugins: No plugin configuration file found: %s", this.filename),
          e);
    }
    addJarsToClassPath(source, urls);
  }

  /**
   * Convert inputStream to pluginProperties object
   *
   * @param inputStream The list of plugins
   * @return PluginPropertyDetails
   */
  private PluginPropertyDetails parsePluginConfigs(InputStream inputStream) {
    ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());
    try {
      PluginPropertyDetails pluginPropertyDetails =
          objectMapper.readValue(inputStream, PluginProperties.class).getPluginsPropertyDetails();
      pluginPropertyDetails.validate();
      return pluginPropertyDetails;
    } catch (IOException e) {
      throw new MalformedPluginConfigurationException("Unable to parse plugin configurations", e);
    }
  }

  /**
   * @return InputStream of config file
   * @throws FileNotFoundException
   */
  private InputStream openFromFile() throws FileNotFoundException {
    return new FileInputStream(this.filename);
  }

  /** @return returns true if the plugin configuration file exists */
  private boolean checkFileExists() {
    File file = new File(this.filename);
    return file.exists();
  }

  /**
   * This method returns a list of Plugin Configurations that are enabled
   *
   * @param pluginPropertyDetails Plugin configs, deserialized
   * @return List<PluginConfiguration> List of pluginConfigurations where enabled is true
   */
  private List<PluginConfiguration> getEnabledJars(PluginPropertyDetails pluginPropertyDetails) {
    return pluginPropertyDetails.getPluginConfigurations().stream()
        .filter(p -> p.enabled == true)
        .collect(Collectors.toList());
  }

  /**
   * @param pluginConfigurations
   * @return List<URL> List of paths to jars, where enabled is true
   */
  private URL[] getJarPathsFromPluginConfigurations(
      List<PluginConfiguration> pluginConfigurations, boolean downloadingEnabled) {
    return pluginConfigurations.stream()
        .map(PluginConfiguration::getJars)
        .flatMap(Collection::stream)
        .map(s -> convertToUrl(s, downloadingEnabled))
        .distinct()
        .toArray(URL[]::new);
  }

  /**
   * @param jarLocation
   * @return path as a URL
   */
  private URL convertToUrl(String jarLocation, boolean downloadingEnabled) {
    try {
      if (jarLocation.startsWith("/")) {
        return Paths.get(jarLocation).toUri().toURL();
      } else if (jarLocation.startsWith("file://") || downloadingEnabled) {
        return new URL(jarLocation);
      } else {
        throw new MalformedPluginConfigurationException(
            "Attempting to download jar " + jarLocation + " but downloading is disabled");
      }
    } catch (MalformedURLException | NullPointerException e) {
      throw new MalformedPluginConfigurationException(e);
    }
  }

  /**
   * Log plugin info for enabled jars
   *
   * @param pluginConfigurations
   */
  private void logPlugins(List<PluginConfiguration> pluginConfigurations) {
    if (!pluginConfigurations.isEmpty()) {
      for (PluginConfiguration pluginConfiguration : pluginConfigurations) {
        log.info("Loading {}", pluginConfiguration);
      }
    } else {
      log.info("Did not find any plugins to load");
    }
  }

  /**
   * Adds jars to the classpath.
   *
   * @param source the parent class loader for delegation
   */
  private void addJarsToClassPath(Class source, URL[] urls) {
    URL[] currentURLsArray =
        ((URLClassLoader) Thread.currentThread().getContextClassLoader()).getURLs();
    URL[] all =
        Stream.concat(Arrays.stream(currentURLsArray), Arrays.stream(urls)).toArray(URL[]::new);
    Thread.currentThread().setContextClassLoader(new URLClassLoader(all, source.getClassLoader()));
  }
}
