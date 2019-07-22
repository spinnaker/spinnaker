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

import com.google.common.annotations.Beta;
import java.util.Map;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Projects that want to support plugins should extend SpinnakerApplication instead of
 * SpringBootServletInitializer.
 */
@Beta
public class SpinnakerApplication extends SpringBootServletInitializer {

  /**
   * This method initializes a default PluginLoader to use for loading plugins. When extending
   * SpinnakerApplication this method should be used, as PluginLoader provides sane defaults.
   *
   * @param defaultProps Properties to pass to spring application
   * @param source The parent class loader for delegation
   * @param args The application arguments (usually passed from a Java main method)
   */
  public static void initialize(Map<String, Object> defaultProps, Class source, String... args) {
    PluginLoader pluginLoader = new PluginLoader();
    initialize(pluginLoader, defaultProps, source, args);
  }

  /**
   * This method wraps the call to SpringApplicationBuilder after loading plugins to ensure plugins
   * are loaded prior to spring context initialization.
   *
   * @param pluginLoader
   * @param defaultProps
   * @param source
   * @param args
   */
  public static void initialize(
      PluginLoader pluginLoader, Map<String, Object> defaultProps, Class source, String... args) {
    pluginLoader.loadPlugins(source);
    new SpringApplicationBuilder().properties(defaultProps).sources(source).run(args);
  }
}
