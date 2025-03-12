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
package com.netflix.spinnaker.front50.model.plugins;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.front50.echo.Event;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode()
@Data
public class PluginEvent implements Event {
  private final Content content;
  private final Map<String, Object> details;

  public PluginEvent(
      PluginEventType type, PluginInfo pluginInfo, PluginInfo.Release pluginRelease) {
    this.content = new Content(pluginInfo, pluginRelease);

    this.details =
        ImmutableMap.<String, Object>builder()
            .put("type", "plugin")
            .put("source", "front50")
            .put(
                "attributes",
                ImmutableMap.<String, String>builder().put("pluginEventType", type.name()).build())
            .build();
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Content {
    private String pluginId;
    private String description;
    private String provider;
    private String version;
    private String releaseDate;
    private String requires;
    private List<PluginInfo.ServiceRequirement> parsedRequires;
    private String binaryUrl;
    private String sha512sum;
    private boolean preferred;
    private String lastModified;

    public Content(PluginInfo pluginInfo, PluginInfo.Release pluginRelease) {
      this.pluginId = pluginInfo.getId();
      this.description = pluginInfo.getDescription();
      this.provider = pluginInfo.getProvider();
      this.version = pluginRelease.getVersion();
      this.releaseDate = pluginRelease.getDate();
      this.requires = pluginRelease.getRequires();
      this.parsedRequires = pluginRelease.getParsedRequires();
      this.binaryUrl = pluginRelease.getUrl();
      this.sha512sum = pluginRelease.getSha512sum();
      this.preferred = pluginRelease.isPreferred();
      Instant lastModified = pluginRelease.getLastModified();
      this.lastModified = lastModified != null ? lastModified.toString() : null;
    }
  }
}
