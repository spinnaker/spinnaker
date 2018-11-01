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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedContent;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@Data
public class V2PipelineTemplate implements VersionedSchema {

  public static final String SCHEMA = "schema";
  public static final String V2_SCHEMA_VERSION = "v2";

  private String schema;
  private String id;
  private Metadata metadata = new Metadata();

  /**
   * protect specifies whether a pipeline template's stage graph is mutable by configurations.
   */
  private Boolean protect = false;
  private List<Variable> variables = new ArrayList<>();
  private Configuration configuration;
  private List<StageDefinition> stages;

  @Data
  public static class Metadata {
    public static final String TEMPLATE_VALID_NAME_REGEX = "^[a-zA-z0-9-&\\s]+$";
    private String name;
    private String description;
    private String owner;
    private List<String> scopes = new ArrayList<>();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Variable implements NamedContent<Variable>, Cloneable {
    public static final String TEMPLATE_VALID_VARIABLE_NAME_REGEX = "^[a-zA-Z0-9-_]+$";
    private String name;
    private String description;
    private String type;
    private Object defaultValue;
    private String example;
    private boolean nullable;
    private boolean merge = false;
    private boolean remove = false;

    public String getType() {
      return Optional.ofNullable(type).orElse("object");
    }

    public boolean hasDefaultValue() {
      return defaultValue != null;
    }

    @Override
    public Variable merge(Variable overlay) {
      Variable v;
      try {
        v = (Variable) this.clone();
      } catch (CloneNotSupportedException e) {
        throw new RuntimeException("Could not clone Variable", e);
      }
      if (overlay.description != null) { v.description = overlay.description; }
      if (overlay.type != null) { v.type = overlay.type; }
      if (overlay.defaultValue != null) { v.defaultValue = overlay.defaultValue; }
      if (overlay.example != null) { v.example = overlay.example; }
      return v;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  public static class Configuration extends HashMap<String, Object> {}

  @Override
  @JsonIgnore
  public String getSchemaVersion() {
    return schema;
  }

  public Configuration getConfiguration() {
    return Optional.ofNullable(configuration).orElse(new Configuration());
  }

  public List<StageDefinition> getStages() {
    return Optional.ofNullable(stages).orElse(Collections.emptyList());
  }
}
