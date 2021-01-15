/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.kubernetes.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.wnameless.json.flattener.JsonFlattener;
import com.github.wnameless.json.unflattener.JsonUnflattener;
import com.netflix.spinnaker.kork.configserver.CloudConfigResourceService;
import com.netflix.spinnaker.kork.secrets.EncryptedSecret;
import com.netflix.spinnaker.kork.secrets.SecretManager;
import com.netflix.spinnaker.kork.secrets.SecretSession;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.cloud.bootstrap.config.BootstrapPropertySource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

/**
 * For larger number of Kubernetes accounts, as-is SpringBoot implementation of properties binding
 * is inefficient, hence a custom logic for KubernetesConfigurationProperties is written but it
 * still uses SpringBoot's Binder class. BootstrapKubernetesConfigurationProvider class fetches the
 * flattened kubernetes properties from Spring Cloud Config's BootstrapPropertySource and creates a
 * KubernetesConfigurationProperties object.
 */
public class BootstrapKubernetesConfigurationProvider {
  private final ConfigurableApplicationContext applicationContext;
  private CloudConfigResourceService configResourceService;
  private SecretSession secretSession;
  private Map<String, String> configServerCache;
  private ObjectMapper objectMapper = new ObjectMapper();
  private final String FIRST_ACCOUNT_NAME_KEY = "kubernetes.accounts[0].name";

  public BootstrapKubernetesConfigurationProvider(
      ConfigurableApplicationContext applicationContext,
      CloudConfigResourceService configResourceService,
      SecretManager secretManager) {
    this.applicationContext = applicationContext;
    this.configResourceService = configResourceService;
    this.secretSession = new SecretSession(secretManager);
  }

  public KubernetesConfigurationProperties getKubernetesConfigurationProperties() {
    return getKubernetesConfigurationProperties(getPropertiesMap());
  }

  @SuppressWarnings("unchecked")
  public KubernetesConfigurationProperties getKubernetesConfigurationProperties(
      Map<String, Object> kubernetesPropertiesMap) {
    KubernetesConfigurationProperties k8sConfigProps = new KubernetesConfigurationProperties();
    BindResult<?> result;

    // unflatten
    Map<String, Object> propertiesMap =
        (Map<String, Object>)
            JsonUnflattener.unflattenAsMap(kubernetesPropertiesMap).get("kubernetes");

    // loop through each account and bind
    for (Map<String, Object> unflattendAcc :
        ((List<Map<String, Object>>) propertiesMap.get("accounts"))) {
      result =
          bind(getFlatMap(unflattendAcc), KubernetesConfigurationProperties.ManagedAccount.class);
      k8sConfigProps
          .getAccounts()
          .add((KubernetesConfigurationProperties.ManagedAccount) result.get());
    }

    return k8sConfigProps;
  }

  private BindResult<?> bind(Map<String, Object> propertiesMap, Class<?> clazz) {
    resolveSpecialCases(propertiesMap);
    ConfigurationPropertySource configurationPropertySource =
        new MapConfigurationPropertySource(propertiesMap);
    Iterable<ConfigurationPropertySource> sourceIterable =
        () -> Collections.singleton(configurationPropertySource).iterator();
    Binder binder =
        new Binder(
            sourceIterable,
            new PropertySourcesPlaceholdersResolver(applicationContext.getEnvironment()));
    return binder.bind("", Bindable.of(clazz));
  }

  private void resolveSpecialCases(Map<String, Object> propertiesMap) {
    String result;
    for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
      if (entry.getValue() instanceof String) {
        result = resolveConfigServerPattern((String) entry.getValue());
        result = resolveEncryptedPattern(result);
        entry.setValue(result);
      }
    }
  }

  private Map<String, Object> getFlatMap(Map<String, Object> unflatMap) {
    try {
      return JsonFlattener.flattenAsMap(objectMapper.writeValueAsString(unflatMap));
    } catch (JsonProcessingException e) {
      throw new RuntimeException(
          "Error occurred while building KubernetesConfigurationProperties object: "
              + e.getMessage());
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getPropertiesMap() {
    ConfigurableEnvironment environment = applicationContext.getEnvironment();
    Map<String, Object> map;

    for (PropertySource<?> propertySource : environment.getPropertySources()) {

      if (propertySource instanceof BootstrapPropertySource) {
        map = (Map<String, Object>) propertySource.getSource();
        if (map.containsKey(FIRST_ACCOUNT_NAME_KEY)) {
          return map;
        }
      }

      if (propertySource.getSource() instanceof BootstrapPropertySource) {
        BootstrapPropertySource<Map<String, Object>> bootstrapPropertySource =
            (BootstrapPropertySource<Map<String, Object>>) propertySource.getSource();
        if (bootstrapPropertySource.containsProperty(FIRST_ACCOUNT_NAME_KEY)) {
          return bootstrapPropertySource.getSource();
        }
      }
    }

    throw new RuntimeException("No BootstrapPropertySource found!!!!");
  }

  private String resolveEncryptedPattern(String possiblePattern) {
    if (possiblePattern.startsWith(EncryptedSecret.ENCRYPTED_STRING_PREFIX)) {
      possiblePattern = secretSession.decrypt(possiblePattern);
    }
    return possiblePattern;
  }

  private String resolveConfigServerPattern(String possiblePattern) {
    if (possiblePattern.startsWith("configserver:")) {
      possiblePattern = resolveConfigServerFilePath(possiblePattern);
    }
    return possiblePattern;
  }

  private String resolveConfigServerFilePath(String key) {
    String filePath;

    if (cacheContainsKey(key)) {
      filePath = configServerCache.get(key);
      if (resourceExist(filePath)) {
        return filePath;
      }
    }

    filePath = configResourceService.getLocalPath(key);
    addToCache(key, filePath);
    return filePath;
  }

  private boolean resourceExist(String filePath) {
    return Path.of(filePath).toFile().isFile();
  }

  private void addToCache(String key, String filePath) {
    configServerCache.put(key, filePath);
  }

  private boolean cacheContainsKey(String key) {
    if (configServerCache == null) {
      configServerCache = new HashMap<>();
      return false;
    }
    return configServerCache.containsKey(key);
  }
}
