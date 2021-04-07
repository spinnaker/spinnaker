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

package com.netflix.spinnaker.clouddriver.aws.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;

public class TestUtils {

  public static List<Resource> loadResourcesFromDir(String path) {
    try {
      return Arrays.asList(
          ResourcePatternUtils.getResourcePatternResolver(new DefaultResourceLoader())
              .getResources(path));
    } catch (IOException ex) {
      throw new RuntimeException("Failed to load resources from directory " + path, ex);
    }
  }

  public static TestResourceFile loadJson(Resource resource) {
    try (InputStream is = resource.getInputStream()) {
      ObjectMapper objectMapper = new ObjectMapper();
      JsonNode jsonNode = objectMapper.readTree(is);
      List<Map<String, Object>> content;

      if (jsonNode.isArray()) {
        content = objectMapper.convertValue(jsonNode, new TypeReference<>() {});
      } else {
        content =
            Collections.singletonList(
                objectMapper.convertValue(jsonNode, new TypeReference<>() {}));
      }
      return new TestResourceFile(content);
    } catch (IOException ex) {
      throw new RuntimeException(
          "Failed to load test input from file " + resource.getFilename(), ex);
    }
  }

  public static TestResourceFile loadJson(String path) {
    ResourceLoader resourceLoader = new DefaultResourceLoader();
    return loadJson(resourceLoader.getResource(path));
  }

  public static class TestResourceFile {
    private final List<Map<String, Object>> content;

    public TestResourceFile(List<Map<String, Object>> content) {
      this.content = content;
    }

    public List<Map<String, Object>> asList() {
      return content;
    }

    public Map<String, Object> asMap() {
      return content.get(0);
    }

    @SuppressWarnings("unchecked")
    public TestResourceFile withValue(String path, Object value) {
      List<String> parts = Splitter.on('.').splitToList(path);

      for (Map<String, Object> entry : content) {
        for (int i = 0; i < parts.size(); i++) {
          if (parts.get(i).matches("^.*\\[[0-9]*]$")) {
            String key = parts.get(i).substring(0, parts.get(i).indexOf('['));
            int index =
                Integer.parseInt(
                    parts
                        .get(i)
                        .substring(parts.get(i).indexOf('[') + 1, parts.get(i).indexOf(']')));
            List<Map<String, Object>> list = (List<Map<String, Object>>) entry.get(key);
            if (i == parts.size() - 1) {
              list.add(index, (Map<String, Object>) value);
              break;
            }
            entry = list.get(index);
          } else if (i == parts.size() - 1) {
            entry.put(parts.get(i), value);
            break;
          } else if (!entry.containsKey(parts.get(i))) {
            entry.put(parts.get(i), new HashMap<>());
            entry = (Map<String, Object>) entry.get(parts.get(i));
          } else {
            entry = (Map<String, Object>) entry.get(parts.get(i));
          }
        }
      }

      return this;
    }
  }
}
