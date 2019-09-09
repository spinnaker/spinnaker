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
package com.netflix.spinnaker.kork.test.mimicker;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TreeTraversingParser;
import com.netflix.spinnaker.kork.test.KorkTestException;
import com.netflix.spinnaker.kork.test.MapUtils;
import java.io.IOException;
import java.io.InputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.yaml.snakeyaml.Yaml;

public class DataContainer {

  private static final Logger log = LoggerFactory.getLogger(DataContainer.class);

  private final SecureRandom random = new SecureRandom();
  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
  private final Yaml yaml = new Yaml();

  private final Map<String, Object> data = new HashMap<>();

  public DataContainer() {
    withDefaultResources();
  }

  public DataContainer(List<String> resourcePaths) {
    for (String path : resourcePaths) {
      load(path);
    }
  }

  @NotNull
  public DataContainer withDefaultResources() {
    load("mimicker.yml");
    return this;
  }

  public DataContainer load(String resourcePath) {
    log.debug("Loading data: {}", resourcePath);

    ClassPathResource resource = new ClassPathResource(resourcePath);
    try (InputStream is = resource.getInputStream()) {
      data.putAll(MapUtils.merge(data, yaml.load(is)));
    } catch (IOException e) {
      throw new KorkTestException(format("Failed reading mimic data: %s", resourcePath), e);
    }
    return this;
  }

  @NotNull
  public String get(@NotNull String key) {
    return getOfType(key, String.class);
  }

  public boolean exists(@NotNull String key) {
    return !objectMapper.valueToTree(data).at(normalizeKey(key)).isMissingNode();
  }

  @NotNull
  @SuppressWarnings("unchecked")
  public <T> List<@NotNull T> list(@NotNull String key) {
    List value = getOfType(key, List.class);
    return (List<T>) value;
  }

  @NotNull
  public String random(@NotNull String key) {
    List candidates = getOfType(key, List.class);
    return (String) candidates.get(random.nextInt(candidates.size()));
  }

  @NotNull
  public <T> T getOfType(@NotNull String key, Class<T> type) {
    JsonParser parser =
        new TreeTraversingParser(
            objectMapper.valueToTree(data).at(normalizeKey(key)), objectMapper);
    try {
      return objectMapper.readValue(parser, type);
    } catch (IOException e) {
      throw new KorkTestException(format("Unable to map '%s' to %s", key, type), e);
    }
  }

  private String normalizeKey(@NotNull String key) {
    if (key.startsWith("/mimicker/")) {
      return key;
    }
    return format("/mimicker/%s", key);
  }
}
