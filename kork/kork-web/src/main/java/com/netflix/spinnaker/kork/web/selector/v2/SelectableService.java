/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.selector.v2;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.util.*;
import java.util.function.Function;

/**
 * Provides support for sharding a service based on {@link Parameter}. Example of a service
 * definition:
 *
 * <pre>
 * bakery:
 *  baseUrl: http://bakery.com
 *  altBakeryApiEnabled: false
 *  timeoutMs: 3000
 *  baseUrls:
 *  - baseUrl: http://alt-bakery.us-west-x.com
 *    priority: 1
 *    config:
 *      altBakeryApiEnabled: true
 *      maxItemsCached: 10
 *    parameters:
 *    - name: region
 *      values:
 *      - us-west-1
 *      - us-west-2
 *  - baseUrl: http://alt-bakery.other.com
 *    priority: 2
 *    config:
 *      altBakeryApiEnabled: true
 *    parameters:
 *    - name: artifactType
 *      values:
 *      - RPM
 *    - name: baseOS
 *      values:
 *      - centOS
 *    - name: user
 *      values:
 *      - regx:^[a-f].+@netflix.com
 * </pre>
 *
 * Usage:
 *
 * <pre>{@code
 * val config = mapOf("timeoutMs", 3000)
 * val selectableBakery: SelectableService<BakeryService> = SelectableService(
 *   baseUrls: properties.baseUrls,
 *   defaultService: bakery,
 *   defaultConfig: config,
 *   getServiceByUrlFx: { url -> getService(url)}
 * )
 *
 * // select by artifact type
 * val authenticatedUser = "alice@netflix.com"
 * val params = listOf(Parameter("artifactType", listOf("RPM")), Parameter("user", listOf(authenticatedUser)))
 * val bakery = selectableBakery.byParameters(params)
 * val result = bakery.getService().createBake(request)
 *
 * // configuration {@link BaseUrl#config} attributes for each service definition are accessible like this
 * if (bakery.config["altBakeryApiEnabled"]) {
 *     // Do interesting things
 * }
 *
 * assert bakery.baseUrl == "http://alt-bakery.other.com"
 * assert bakery.config == mapOf("timeoutMs" to 3000, "altBakeryApiEnabled" to true)
 * }</pre>
 *
 * @param <T> the type of service to be selected
 */
public class SelectableService<T> {
  private final Map<BaseUrl, T> services;
  private final T defaultService;
  private final Map<String, Object> defaultConfig;

  public SelectableService(
      List<BaseUrl> baseUrls,
      T defaultService,
      Map<String, Object> defaultConfig,
      Function<String, T> getServiceByUrlFx) {
    this.defaultService = defaultService;
    this.defaultConfig = defaultConfig;
    this.services = buildServices(baseUrls, getServiceByUrlFx);
  }

  /**
   * Selects a service based on input {@link Parameter} as criteria
   *
   * @param inputParameters parameters to use to select a service
   * @return new {@link SelectedService} containing the selecting parameter.
   */
  public SelectedService<T> byParameters(List<Parameter> inputParameters) {
    final SelectedService<T> fallback = getDefaultService();
    if (inputParameters == null || inputParameters.isEmpty()) {
      return fallback;
    }

    for (Map.Entry<BaseUrl, T> urlToService : services.entrySet()) {
      if (inputParameters.containsAll(urlToService.getKey().parameters)) {
        return new SelectedService<>(
            urlToService.getValue(), urlToService.getKey().getConfig(), inputParameters);
      }
    }

    return fallback;
  }

  /**
   * Returns a dictionary of {@link BaseUrl} objects to T services, additionally overlaying the
   * default config to each service definition (if provided)
   *
   * <p>Note that each nested service definition will inherit (and can override) the default
   * configuration.
   *
   * @param baseUrls a list of service or shard definitions
   * @param getServiceByUrlFx a function to get a service by url
   * @return a {@link BaseUrl} to T services
   */
  private Map<BaseUrl, T> buildServices(
      List<BaseUrl> baseUrls, Function<String, T> getServiceByUrlFx) {
    return baseUrls.stream()
        .sorted(BaseUrl::compareTo)
        .peek(baseUrl -> defaultConfig.forEach((k, v) -> baseUrl.config.putIfAbsent(k, v)))
        .collect(
            toMap(
                baseUrl -> baseUrl,
                baseUrl -> getServiceByUrlFx.apply(baseUrl.baseUrl),
                (a, b) -> b,
                LinkedHashMap::new));
  }

  public SelectedService<T> getDefaultService() {
    return new SelectedService<>(defaultService, defaultConfig, null);
  }

  public Map<BaseUrl, T> getServices() {
    return services;
  }

  /**
   * Represents a selected service
   *
   * @param <T> the type of selected service
   */
  public static class SelectedService<T> {
    private final T service;

    /** Configuration attributes associated with this selection. See {@link BaseUrl#config} */
    private final Map<String, Object> config;

    /** Parameters used in this selection */
    private final List<Parameter> selectingParameters;

    public SelectedService(
        T service, Map<String, Object> config, List<Parameter> selectingParameters) {
      this.service = service;
      this.config = config;
      this.selectingParameters = selectingParameters;
    }

    public T getService() {
      return service;
    }

    public Map<String, Object> getConfig() {
      return config;
    }

    public List<Parameter> getSelectingParameters() {
      return selectingParameters;
    }
  }

  public static class BaseUrl implements Comparable {
    private String baseUrl;
    private int priority;
    private Map<String, Object> config;
    private List<Parameter> parameters;

    public Map<String, Object> getConfig() {
      return config;
    }

    public void setConfig(Map<String, Object> config) {
      this.config = config;
    }

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public int getPriority() {
      return priority;
    }

    public void setPriority(int priority) {
      this.priority = priority;
    }

    public List<Parameter> getParameters() {
      return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
      this.parameters = parameters;
    }

    @Override
    public int compareTo(Object o) {
      if (o instanceof BaseUrl) {
        final BaseUrl other = (BaseUrl) o;
        if (priority == other.priority) {
          return 0;
        } else if (priority < other.priority) {
          return -1;
        }
      }

      return 1;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }

      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      BaseUrl baseUrl1 = (BaseUrl) o;
      return priority == baseUrl1.priority
          && Objects.equals(baseUrl, baseUrl1.baseUrl)
          && Objects.equals(config, baseUrl1.config)
          && Objects.equals(parameters, baseUrl1.parameters);
    }

    @Override
    public int hashCode() {
      return Objects.hash(baseUrl, priority, config, parameters);
    }
  }

  public static class Parameter {
    private String name;
    private List<Object> values;

    public Parameter() {
      this.values = new ArrayList<>();
    }

    Parameter(Map<String, Object> source) {
      this.name = source.get("name").toString();
      this.values = (List<Object>) source.get("values");
    }

    public Parameter(String name, List<Object> values) {
      this.name = name;
      this.values = values;
    }

    public Parameter withName(String name) {
      this.setName(name);
      return this;
    }

    public Parameter withValues(List<Object> values) {
      this.setValues(values);
      return this;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Object> getValues() {
      return values;
    }

    public void setValues(List<Object> values) {
      this.values = values;
    }

    static List<Parameter> toParameters(List<Map<String, Object>> source) {
      return source.stream().map(Parameter::new).collect(toList());
    }

    @Override
    public boolean equals(Object o) {
      if (o instanceof Parameter) {
        Parameter other = (Parameter) o;
        if (!this.name.equals(other.getName())) {
          return false;
        }

        for (Object v : values) {
          if (v instanceof String && ((String) v).startsWith("regex:")) {
            final String regex = ((String) v).substring(((String) v).indexOf(":") + 1);
            if (other.getValues().stream().anyMatch(i -> ((String) i).matches(regex))) {
              return true;
            }
          } else {
            if (other.getValues().stream().anyMatch(i -> i.equals(v))) {
              return true;
            }
          }
        }
      }

      return false;
    }

    @Override
    public int hashCode() {
      return Objects.hash(name, values);
    }

    @Override
    public String toString() {
      return "Parameter{" + "name='" + name + '\'' + ", values=" + values + '}';
    }
  }
}
