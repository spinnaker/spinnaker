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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DefaultVariableAssignmentTransform implements PipelineTemplateVisitor {

  TemplateConfiguration templateConfiguration;

  public DefaultVariableAssignmentTransform(TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(PipelineTemplate pipelineTemplate) {
    if (pipelineTemplate.getVariables() == null || pipelineTemplate.getVariables().isEmpty()) {
      return;
    }

    Map<String, Object> configVars = templateConfiguration.getPipeline().getVariables() != null
      ? templateConfiguration.getPipeline().getVariables()
      : new HashMap<>();

    // if the config is missing vars and the template defines a default value, assign those values from the config
    pipelineTemplate.getVariables().stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()) && templateVar.hasDefaultValue())
      .forEach(templateVar -> configVars.put(templateVar.getName(), templateVar.getDefaultValue()));

    List<String> missingVariables = pipelineTemplate.getVariables().stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()))
      .map(Variable::getName)
      .collect(Collectors.toList());

    if (!missingVariables.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Missing variable values for: " + StringUtils.join(missingVariables, ", "));
    }

    // TODO rz - validate variable values match the defined variable type
  }
}
