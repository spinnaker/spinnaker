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
package com.netflix.spinnaker.orca.pipelinetemplate;

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;

import java.util.Map;

public class TemplatedPipelineRequest {
  String id;
  String type;
  Map<String, Object> trigger;
  TemplateConfiguration config;
  PipelineTemplate template;
  Boolean plan = false;
  boolean limitConcurrent = true;
  boolean keepWaitingPipelines = false;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public boolean isTemplatedPipelineRequest() {
    return "templatedPipeline".equals(type);
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public TemplateConfiguration getConfig() {
    return config;
  }

  public void setConfig(TemplateConfiguration config) {
    this.config = config;
  }

  public Map<String, Object> getTrigger() {
    return trigger;
  }

  public void setTrigger(Map<String, Object> trigger) {
    this.trigger = trigger;
  }

  public PipelineTemplate getTemplate() {
    return template;
  }

  public void setTemplate(PipelineTemplate template) {
    this.template = template;
  }

  public Boolean getPlan() {
    return plan;
  }

  public void setPlan(Boolean plan) {
    this.plan = plan;
  }

  public boolean isLimitConcurrent() {
    return limitConcurrent;
  }

  public boolean isKeepWaitingPipelines() {
    return keepWaitingPipelines;
  }
}
