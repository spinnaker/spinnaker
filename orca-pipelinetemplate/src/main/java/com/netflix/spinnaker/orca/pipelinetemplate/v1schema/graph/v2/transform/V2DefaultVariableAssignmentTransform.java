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

package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform;

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException;
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.V2PipelineTemplateVisitor;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate;
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class V2DefaultVariableAssignmentTransform implements V2PipelineTemplateVisitor {

  private V2TemplateConfiguration templateConfiguration;

  public V2DefaultVariableAssignmentTransform(V2TemplateConfiguration templateConfiguration) {
    this.templateConfiguration = templateConfiguration;
  }

  @Override
  public void visitPipelineTemplate(V2PipelineTemplate pipelineTemplate) {
    List<V2PipelineTemplate.Variable> pipelineTemplateVariables = pipelineTemplate.getVariables();
    if (pipelineTemplateVariables == null || pipelineTemplateVariables.isEmpty()) {
      return;
    }

    Map<String, Object> configVars = templateConfiguration.getVariables() != null
      ? templateConfiguration.getVariables()
      : new HashMap<>();

    // if the config is missing vars and the template defines a default value, assign those values from the config
    pipelineTemplateVariables.stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()) && templateVar.hasDefaultValue())
      .forEach(templateVar -> configVars.put(templateVar.getName(), templateVar.getDefaultValue()));

    List<String> missingVariables = pipelineTemplateVariables.stream()
      .filter(templateVar -> !configVars.containsKey(templateVar.getName()) && !templateVar.isNullable())
      .map(V2PipelineTemplate.Variable::getName)
      .collect(Collectors.toList());

    if (!missingVariables.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Missing variable values for: " + StringUtils.join(missingVariables, ", "));
    }

    List<String> wrongNullableErrorMessages = pipelineTemplateVariables.stream()
      .filter(templateVar -> !templateVar.isNullable() && configVars.get(templateVar.getName()) == null)
      .map(var -> String.format("variable '%s' supplied value is null but variable is not nullable\n", var.getName()))
      .collect(Collectors.toList());
    if (!wrongNullableErrorMessages.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Incorrectly defined variable(s): " + StringUtils.join(wrongNullableErrorMessages, ", "));
    }

    // collect variables where value type doesn't match the required type
    List<String> wrongTypeErrorMessages = pipelineTemplateVariables.stream()
      .filter(templateVar -> {
        Object actualVar = configVars.get(templateVar.getName());

        String expectedType = templateVar.getType();
        if (expectedType.equalsIgnoreCase("object")) {
          return false; // Not invalid, all classes are objects
        } else if (templateVar.isNullable() && actualVar == null) {
          return false; // Not invalid, can't determine type from null value
        }

        Class<?> actualType = actualVar.getClass();
        return !(
          (isInteger(templateVar, actualVar)) ||
            (expectedType.equalsIgnoreCase("bool") && actualVar instanceof Boolean) ||
            (expectedType.equalsIgnoreCase("list") && actualVar instanceof Collection) ||
            (expectedType.equalsIgnoreCase("string") && actualVar instanceof CharSequence) ||
            (expectedType.equalsIgnoreCase("float") && (actualVar instanceof Double || actualVar instanceof Float)) ||
            (expectedType.equalsIgnoreCase(actualType.getSimpleName()))
        );
      })
      .map(var -> var.getName() + " (expected type '" + var.getType() + "' found type '" + configVars.get(var.getName()).getClass().getSimpleName() + "')")
      .collect(Collectors.toList());

    if (!wrongTypeErrorMessages.isEmpty()) {
      throw new IllegalTemplateConfigurationException("Incorrectly defined variable(s): " + StringUtils.join(wrongTypeErrorMessages, ", "));
    }
  }

  /*
  Note: Echo and Orca have separate views of the pipeline store. Since templated pipeline configs do not
  contain enough information for Echo to intuit the type of the template variables, we have to be lenient
  here during validation and interpret the variable types.
   */
  private boolean isInteger(V2PipelineTemplate.Variable templateVar, Object actualVar) {
    boolean instanceOfDouble = actualVar instanceof Double;
    boolean instanceOfFloat = actualVar instanceof Float;
    boolean noDecimal = true;
    if (instanceOfDouble) {
      Double actualDouble = (double) actualVar;
      noDecimal = actualDouble % 1 == 0;
    } else if (instanceOfFloat) {
      Float actualFloat = (float) actualVar;
      noDecimal = actualFloat % 1 == 0;
    }
    String expectedtype = templateVar.getType();
    return expectedtype.equalsIgnoreCase("int") &&
      (actualVar instanceof Integer || (noDecimal && instanceOfDouble) || (noDecimal && instanceOfFloat));
  }
}
