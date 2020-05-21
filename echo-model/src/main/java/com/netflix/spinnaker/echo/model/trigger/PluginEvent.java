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
 */
package com.netflix.spinnaker.echo.model.trigger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class PluginEvent extends TriggerEvent {
  public static final String TYPE = "plugin";

  private Content content;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Content {
    private String pluginId;
    private String version;
    private String releaseDate;
    private String requires;
    private List<Map<String, String>> parsedRequires;
    private String binaryUrl;
    private String sha512sum;
    private boolean preferred;
    private String lastModified;
  }
}
