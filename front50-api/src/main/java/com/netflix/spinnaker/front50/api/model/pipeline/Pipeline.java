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
package com.netflix.spinnaker.front50.api.model.pipeline;

import com.netflix.spinnaker.front50.api.model.Timestamped;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

public class Pipeline implements Timestamped {

  public static final String TYPE_TEMPLATED = "templatedPipeline";

  private Map<String, Object> anyMap = new HashMap<>();

  @Setter private String id;
  @Getter @Setter private String name;
  @Getter @Setter private String application;
  @Getter @Setter private String type;
  @Setter private String schema;
  @Getter @Setter private Object config;
  @Getter @Setter private List<Trigger> triggers = new ArrayList<>();
  @Getter @Setter private Integer index;

  @Getter private String updateTs;
  private String createTs;
  private String lastModifiedBy;
  private String lastModified;

  @Getter @Setter private String email;
  @Getter @Setter private Boolean disabled;
  @Getter @Setter private Map<String, Object> template;
  @Getter @Setter private List<String> roles;
  @Getter @Setter private String serviceAccount;
  @Getter @Setter private String executionEngine;
  @Getter @Setter private Integer stageCounter;
  @Getter @Setter private List<Map<String, Object>> stages;
  @Getter @Setter private Map<String, Object> constraints;
  @Getter @Setter private Map<String, Object> payloadConstraints;
  @Getter @Setter private Boolean keepWaitingPipelines;
  @Getter @Setter private Boolean limitConcurrent;
  @Getter @Setter private Integer maxConcurrentExecutions;
  @Getter @Setter private List<Map<String, Object>> parameterConfig;
  @Getter @Setter private String spelEvaluator;

  public void setAny(String key, Object value) {
    anyMap.put(key, value);
  }

  public Map<String, Object> getAny() {
    return anyMap;
  }

  @Override
  public String getId() {
    return this.id;
  }

  @Override
  public Long getLastModified() {
    String updateTs = this.updateTs;
    if (updateTs == null || updateTs == "") {
      return null;
    }
    return Long.valueOf(updateTs);
  }

  @Override
  public void setLastModified(Long lastModified) {
    if (lastModified != null) {
      this.updateTs = lastModified.toString();
    }
  }

  @Override
  public String getLastModifiedBy() {
    return this.lastModifiedBy;
  }

  @Override
  public void setLastModifiedBy(String lastModifiedBy) {
    this.lastModifiedBy = lastModifiedBy;
  }

  /**
   * Denotes templated pipeline config schema version.
   *
   * @return
   */
  public String getSchema() {
    final String get = this.schema;
    if (get == null || get == "") {
      return "1";
    }
    return get;
  }
}
