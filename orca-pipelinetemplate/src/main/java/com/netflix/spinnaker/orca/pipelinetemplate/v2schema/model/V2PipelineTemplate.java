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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedContent;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;
import java.util.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
public class V2PipelineTemplate implements VersionedSchema {

  public static final String SCHEMA = "schema";
  public static final String V2_SCHEMA_VERSION = "v2";

  private String schema;
  private String id;
  private Metadata metadata = new Metadata();

  /** protect specifies whether a pipeline template's stage graph is mutable by configurations. */
  private Boolean protect = false;

  private List<Variable> variables = new ArrayList<>();

  /** pipeline is a possibly SpEL-templated pipeline definition. */
  private Map<String, Object> pipeline;

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
    public static final String TEMPLATE_VALID_VARIABLE_NAME_REGEX = "^[a-zA-Z0-9_]+$";
    private String name;
    private String description;
    private String type;
    private Object defaultValue;
    private String example;
    private boolean nullable;
    private boolean merge;
    private boolean remove;

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
      if (overlay.description != null) {
        v.description = overlay.description;
      }
      if (overlay.type != null) {
        v.type = overlay.type;
      }
      if (overlay.defaultValue != null) {
        v.defaultValue = overlay.defaultValue;
      }
      if (overlay.example != null) {
        v.example = overlay.example;
      }
      return v;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
      return super.clone();
    }
  }

  @Override
  @JsonIgnore
  public String getSchemaVersion() {
    return schema;
  }

  public Map<String, Object> getPipeline() {
    return Optional.ofNullable(pipeline).orElse(Collections.EMPTY_MAP);
  }

  public List<V2StageDefinition> getStages() {
    Object pipelineStages = pipeline.get("stages");
    if (pipelineStages == null) {
      return Collections.emptyList();
    }
    ObjectMapper oj = new ObjectMapper();
    return oj.convertValue(pipelineStages, new TypeReference<List<V2StageDefinition>>() {});
  }

  public void setStages(List<V2StageDefinition> stages) {
    ObjectMapper oj = new ObjectMapper();
    TypeReference mapTypeRef = new TypeReference<List<Map<String, Object>>>() {};
    pipeline.put("stages", oj.convertValue(stages, mapTypeRef));
  }

  public void accept(V2PipelineTemplateVisitor visitor) {
    visitor.visitPipelineTemplate(this);
  }

  public List<Variable> getVariables() {
    return Optional.ofNullable(variables).orElse(Collections.emptyList());
  }
}
