/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import lombok.Data;

import java.util.*;

@Data
public class V2StageDefinition {
  private String refId;
  private String name;
  private StageDefinition.InjectionRule inject;
  /**
   * List of refIds for parent stages this stage depends on.
   */
  private Set<String> requisiteStageRefIds = new LinkedHashSet<>();
  private String type;
  private List<Map<String, Object>> notifications = new ArrayList<>();
  private String comments;

  /**
   * Actual substantive stage config.
   */
  private Map<String, Object> config = new HashMap<>();

  @JsonAnyGetter
  public Map<String, Object> getConfig() {
    return config;
  }

  @JsonAnySetter
  public void setConfig(String key, Object value) {
    this.config.put(key, value);
  }
}
