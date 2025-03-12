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
package com.netflix.spinnaker.igor.plugins.model;

import java.util.List;

public class PluginRelease {
  private final String pluginId;
  private final String description;
  private final String provider;
  private final String version;
  private final String releaseDate;
  private final String requires;
  private final List<ServiceRequirement> parsedRequires;
  private final String binaryUrl;
  private final String sha512sum;
  private final boolean preferred;
  private final String lastModified;

  public PluginRelease(
      String pluginId,
      String description,
      String provider,
      String version,
      String releaseDate,
      String requires,
      List<ServiceRequirement> parsedRequires,
      String binaryUrl,
      String sha512sum,
      boolean preferred,
      String lastModified) {
    this.pluginId = pluginId;
    this.description = description;
    this.provider = provider;
    this.version = version;
    this.releaseDate = releaseDate;
    this.requires = requires;
    this.parsedRequires = parsedRequires;
    this.binaryUrl = binaryUrl;
    this.sha512sum = sha512sum;
    this.preferred = preferred;
    this.lastModified = lastModified;
  }

  public String getPluginId() {
    return pluginId;
  }

  public String getDescription() {
    return description;
  }

  public String getProvider() {
    return provider;
  }

  public String getVersion() {
    return version;
  }

  public String getReleaseDate() {
    return releaseDate;
  }

  public String getRequires() {
    return requires;
  }

  public List<ServiceRequirement> getParsedRequires() {
    return parsedRequires;
  }

  public String getBinaryUrl() {
    return binaryUrl;
  }

  public String getSha512sum() {
    return sha512sum;
  }

  public boolean isPreferred() {
    return preferred;
  }

  public String getLastModified() {
    return lastModified;
  }

  public static class ServiceRequirement {
    private final String service;
    private final String operator;
    private final String version;

    public ServiceRequirement(String service, String operator, String version) {
      this.service = service;
      this.operator = operator;
      this.version = version;
    }

    public String getService() {
      return service;
    }

    public String getOperator() {
      return operator;
    }

    public String getVersion() {
      return version;
    }
  }

  @Override
  public String toString() {
    return "PluginRelease{"
        + "pluginId='"
        + pluginId
        + '\''
        + ", version='"
        + version
        + '\''
        + ", releaseDate='"
        + releaseDate
        + '\''
        + ", preferred="
        + preferred
        + ", lastModified='"
        + lastModified
        + '\''
        + '}';
  }
}
