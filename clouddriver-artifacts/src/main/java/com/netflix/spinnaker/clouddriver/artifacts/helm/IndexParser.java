/*
 * Copyright 2018 Mirantis, Inc.
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

package com.netflix.spinnaker.clouddriver.artifacts.helm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Slf4j
@Data
public class IndexParser {
  private String repository;

  public IndexParser(String repository) {
    this.repository = repository;
  }

  public String indexPath() {
    return repository + "/index.yaml";
  }

  public List<String> findNames(InputStream in) throws IOException {
    IndexConfig indexConfig = buildIndexConfig(in);
    return new ArrayList<>(indexConfig.getEntries().keySet());
  }

  public List<String> findVersions(InputStream in, String name) throws IOException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Artifact name field should not be empty");
    }
    List<EntryConfig> configs = buildEntryConfigsByName(buildIndexConfig(in), name);
    List<String> versions = new ArrayList<>();
    configs.forEach(e -> versions.add(e.getVersion()));
    return versions;
  }

  public List<String> findUrls(InputStream in, String name, String version) throws IOException {
    if (name == null || name.isEmpty()) {
      throw new IllegalArgumentException("Artifact name field should not be empty");
    }
    List<EntryConfig> configs = buildEntryConfigsByName(buildIndexConfig(in), name);
    String validVersion = StringUtils.isBlank(version) ? findLatestVersion(configs) : version;
    return findUrlsByVersion(configs, validVersion);

  }

  private List<String> findUrlsByVersion(List<EntryConfig> configs, String version) {
    List<String> urls = new ArrayList<>();
    configs.forEach(e -> {
      if (e.getVersion().equals(version)) {
        urls.addAll(e.getUrls());
      }
    });
    if (urls.isEmpty()) {
      throw new IllegalArgumentException("Could not find correct entry with artifact version " + version);
    }
    return urls;
  }

  private String findLatestVersion(List<EntryConfig> configs) {
    return configs.stream()
      .max(Comparator.comparing(EntryConfig::getVersion)).get().getVersion();
  }

  private IndexConfig buildIndexConfig(InputStream in) throws IOException {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    IndexConfig indexConfig;
    try {
      indexConfig = mapper.readValue(in, IndexConfig.class);
    } catch (IOException e) {
      throw new IOException("Invalid index.yaml file in repository " + repository);
    }
    return indexConfig;
  }

  private List<EntryConfig>  buildEntryConfigsByName(IndexConfig indexConfig, String name) {
    List<EntryConfig> configs = indexConfig.getEntries().get(name);
    if (configs == null || configs.isEmpty()) {
      throw new IllegalArgumentException("Could not find correct entry with artifact name " + name);
    }
    return configs;
  }

}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class IndexConfig {
  private Map<String, List<EntryConfig>> entries;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EntryConfig {
  private String name;
  private String version;
  private List<String> urls;
}
