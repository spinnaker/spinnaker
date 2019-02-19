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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;
import lombok.Data;

import java.util.*;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class V2TemplateConfiguration implements VersionedSchema {
  private String schema;
  private String application;
  private String pipelineConfigId;
  private String name;
  private Artifact template;
  private Map<String, Object> variables = new HashMap<>();
  private List<V2StageDefinition> stages = new ArrayList<>();
  private List<String> inherit = new ArrayList<>();
  private Map<String, Object> concurrentExecutions = new HashMap<>();
  private List<HashMap<String, Object>> triggers = new ArrayList<>();
  private List<HashMap<String, Object>> parameters = new ArrayList<>();
  private List<HashMap<String, Object>> notifications = new ArrayList<>();
  private List<HashMap<String, Object>> expectedArtifacts = new ArrayList<>();
  private String description;

  private final String runtimeId = UUID.randomUUID().toString();

  @Override
  public String getSchemaVersion() {
    return schema;
  }
}
