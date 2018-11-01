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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netflix.spinnaker.orca.pipelinetemplate.validator.VersionedSchema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.*;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class TemplateConfiguration implements VersionedSchema {

  private String schema;
  private PipelineDefinition pipeline = new PipelineDefinition();
  private PipelineConfiguration configuration = new PipelineConfiguration();
  private List<StageDefinition> stages;
  private List<TemplateModule> modules;
  private List<PartialDefinition> partials = new ArrayList<>();

  private final String runtimeId = UUID.randomUUID().toString();

  @Data
  public static class PipelineDefinition {

    private String application;
    private String pipelineConfigId;
    private String name;
    private TemplateSource template;
    private Map<String, Object> variables = new HashMap<>();

    public String getApplication() {
      return application;
    }

    public void setApplication(String application) {
      this.application = application;
    }

    public String getPipelineConfigId() {
      return pipelineConfigId;
    }

    public void setPipelineConfigId(String pipelineConfigId) {
      this.pipelineConfigId = pipelineConfigId;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public TemplateSource getTemplate() {
      return template;
    }

    public void setTemplate(TemplateSource template) {
      this.template = template;
    }

    public Map<String, Object> getVariables() {
      return variables;
    }

    public void setVariables(Map<String, Object> variables) {
      this.variables = variables;
    }
  }

  @NoArgsConstructor
  @AllArgsConstructor
  public static class TemplateSource {

    private String source;

    public String getSource() {
      return source;
    }

    public void setSource(String source) {
      this.source = source;
    }
  }

  public static class PipelineConfiguration {

    private List<String> inherit = new ArrayList<>();
    private Map<String, Object> concurrentExecutions = new HashMap<>();
    private List<NamedHashMap> triggers = new ArrayList<>();
    private List<NamedHashMap> parameters = new ArrayList<>();
    private List<NamedHashMap> notifications = new ArrayList<>();
    private List<HashMap<String, Object>> expectedArtifacts = new ArrayList<>();
    private String description;

    public List<String> getInherit() {
      return Optional.ofNullable(inherit).orElse(Collections.emptyList());
    }

    public void setInherit(List<String> inherit) {
      this.inherit = inherit;
    }

    public Map<String, Object> getConcurrentExecutions() {
      return concurrentExecutions;
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

    public List<HashMap<String, Object>> getExpectedArtifacts() {
      return expectedArtifacts;
    }

    public void setExpectedArtifacts(List<HashMap<String, Object>> expectedArtifacts) {
      this.expectedArtifacts = expectedArtifacts;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }
  }

  @Override
  public String getSchemaVersion() {
    return schema;
  }

  public String getRuntimeId() {
    return runtimeId;
  }

  public String getSchema() {
    return schema;
  }

  public void setSchema(String schema) {
    this.schema = schema;
  }

  public PipelineDefinition getPipeline() {
    return pipeline;
  }

  public void setPipeline(PipelineDefinition pipeline) {
    this.pipeline = pipeline;
  }

  public PipelineConfiguration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(PipelineConfiguration configuration) {
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
}
