/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class PipelineTemplate implements VersionedSchema {

  private String schema;
  private String id;
  private String source;
  private Metadata metadata = new Metadata();
  private Boolean protect = false;
  private List<Variable> variables;
  private Configuration configuration;
  private List<StageDefinition> stages;
  private List<TemplateModule> modules;
  private List<PartialDefinition> partials = new ArrayList<>();

  public static class Metadata {
    private String name;
    private String description;
    private String owner;
    private List<String> scopes = new ArrayList<>();

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getOwner() {
      return owner;
    }

    public void setOwner(String owner) {
      this.owner = owner;
    }

    public List<String> getScopes() {
      return scopes;
    }

    public void setScopes(List<String> scopes) {
      this.scopes = scopes;
    }
  }

  public static class Variable implements NamedContent {
    private String name;
    private String group = "General";
    private String description;
    private String type = "string";
    private Object defaultValue;
    private String example;

    @Override
    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getGroup() {
      return group;
    }

    public void setGroup(String group) {
      this.group = group;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public Object getDefaultValue() {
      return defaultValue;
    }

    public void setDefaultValue(Object defaultValue) {
      this.defaultValue = defaultValue;
    }

    public boolean hasDefaultValue() {
      return defaultValue != null;
    }

    public String getExample() {
      return example;
    }

    public void setExample(String example) {
      this.example = example;
    }
  }

  public static class Configuration {
    private Map<String, Object> concurrentExecutions;
    private List<NamedHashMap> triggers;
    private List<NamedHashMap> parameters;
    private List<NamedHashMap> notifications;

    public Map<String, Object> getConcurrentExecutions() {
      return Optional.ofNullable(concurrentExecutions).orElse(new HashMap<>());
    }

    public void setConcurrentExecutions(Map<String, Object> concurrentExecutions) {
      this.concurrentExecutions = concurrentExecutions;
    }

    public List<NamedHashMap> getTriggers() {
      return triggers;
    }

    public void setTriggers(List<NamedHashMap> triggers) {
      this.triggers = triggers;
    }

    public List<NamedHashMap> getParameters() {
      return parameters;
    }

    public void setParameters(List<NamedHashMap> parameters) {
      this.parameters = parameters;
    }

    public List<NamedHashMap> getNotifications() {
      return notifications;
    }

    public void setNotifications(List<NamedHashMap> notifications) {
      this.notifications = notifications;
    }
  }

  @Override
  @JsonIgnore
  public String getSchemaVersion() {
    return schema;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Metadata metadata) {
    this.metadata = metadata;
  }

  public Boolean getProtect() {
    return protect;
  }

  public void setProtect(Boolean protect) {
    this.protect = protect;
  }

  public List<Variable> getVariables() {
    return variables;
  }

  public void setVariables(List<Variable> variables) {
    this.variables = variables;
  }

  public Configuration getConfiguration() {
    if (configuration == null) {
      configuration = new Configuration();
    }
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public List<StageDefinition> getStages() {
    return Optional.ofNullable(stages).orElse(Collections.emptyList());
  }

  public void setStages(List<StageDefinition> stages) {
    this.stages = stages;
  }

  public List<TemplateModule> getModules() {
    return modules;
  }

  public void setModules(List<TemplateModule> modules) {
    this.modules = modules;
  }

  public List<PartialDefinition> getPartials() {
    return partials;
  }

  public void setPartials(List<PartialDefinition> partials) {
    this.partials = partials;
  }

  public void accept(PipelineTemplateVisitor visitor) {
    visitor.visitPipelineTemplate(this);
  }
}
