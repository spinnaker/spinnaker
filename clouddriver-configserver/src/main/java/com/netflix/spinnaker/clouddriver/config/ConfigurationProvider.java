/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.config;

import java.util.Map;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.core.env.PropertySource;

/**
 * This interface is meant to provide a custom way of handling configuration properties. Usually,
 * configuration properties are handled implicitly by Spring boot itself. But in case that is not
 * desired, this will allow it to be handled in a custom way where properties are read from a
 * property source, then converted to a map as an intermediate step which is then bound to a target
 * implementation class.
 *
 * <p>This interface defines the necessary actions which would be required to do the same.
 */
public interface ConfigurationProvider<T> {
  /**
   * Returns the desired configuration properties, bound to the target implementation class.
   *
   * @return a target implementation class for a property
   */
  T getConfigurationProperties();

  /**
   * This method takes an input property and returns a map representation of the {@link
   * PropertySource} which contains this property.
   *
   * <p>Loading of candidate property sources and defining a criteria for selecting a property
   * source should be considered when implementing this.
   *
   * @param property A property defined in the configuration file
   * @return a map representation of the {@link PropertySource} that contains the input property.
   */
  Map<String, Object> getPropertiesMap(String property);

  /**
   * This method takes in a property map (which can be nested) and flattens it.
   *
   * <p>For example: input: { name=account, configureImagePullSecrets=true, omitKinds=[podPreset],
   * onlySpinnakerManaged=false } should result into:
   *
   * <p>{ "name":"account", "configureImagePullSecrets":true, "omitKinds[0]":"podPreset",
   * "onlySpinnakerManaged":false }
   *
   * @param unflatMap any type of property map (can be nested)
   * @return a flattened map representation of the input
   */
  Map<String, Object> getFlatMap(Map<String, Object> unflatMap);

  /**
   * This method attempts to bind an input provided as a map to its target implementation class.
   *
   * @param propertiesMap an input of type Map<String, Object> - this should be a flattened map
   * @param clazz the target implementation class
   * @return A {@link BindResult} object
   */
  BindResult<?> bind(Map<String, Object> propertiesMap, Class<?> clazz);
}
