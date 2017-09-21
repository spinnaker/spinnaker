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

import java.util.Collection;
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
    List<Variable> pipelineTemplateVariables = pipelineTemplate.getVariables();
    if (pipelineTemplateVariables == null || pipelineTemplateVariables.isEmpty()) {
      return;
    }

    Map<String, Object> configVars = templateConfiguration.getPipeline().getVariables() != null
      ? templateConfiguration.getPipeline().getVariables()
      : new HashMap<>();

    // if the config is missing vars and the template defines a default value, assign those values from the config
    pipelineTemplateVariables.stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()) && templateVar.hasDefaultValue())
      .forEach(templateVar -> configVars.put(templateVar.getName(), templateVar.getDefaultValue()));

    List<String> missingVariables = pipelineTemplateVariables.stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()))
      .map(Variable::getName)
      .collect(Collectors.toList());

    if (!missingVariables.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Missing variable values for: " + StringUtils.join(missingVariables, ", "));
    }

    // collect variables where value type doesn't match the required type
    List<String> wrongTypeErrorMessages = pipelineTemplateVariables.stream()
      .filter(templateVar -> {
        String expectedType = templateVar.getType();
        if (expectedType.equalsIgnoreCase("object")) {
          return false; // not invalid, all classes are objects
        }

        Class<?> actualType = configVars.get(templateVar.getName()).getClass();
        Object actualVar = configVars.get(templateVar.getName());

        return !(
          (expectedType.equalsIgnoreCase("int") && (actualVar instanceof Integer)) ||
          (expectedType.equalsIgnoreCase("bool") && actualVar instanceof Boolean) ||
          (expectedType.equalsIgnoreCase("list") && actualVar instanceof Collection) ||
          (expectedType.equalsIgnoreCase("string") && actualVar instanceof CharSequence) ||
          (expectedType.equalsIgnoreCase("float") && actualVar instanceof Float) ||
          (expectedType.equalsIgnoreCase(actualType.getSimpleName()))
        );
      })
       .map(var -> var.getName() + " (expected type '" + var.getType() + "' found type '" + configVars.get(var.getName()).getClass().getSimpleName() + "')")
      .collect(Collectors.toList());

    if (!wrongTypeErrorMessages.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Incorrectly defined variable(s): " + StringUtils.join(wrongTypeErrorMessages, ", "));
    }
  }
}
