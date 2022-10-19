/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.orca.capabilities;

import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.ExpressionsSupport;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.orca.capabilities.models.ExpressionCapabilityResult;
import com.netflix.spinnaker.orca.capabilities.models.ExpressionFunctionDefinition;
import com.netflix.spinnaker.orca.capabilities.models.ExpressionSpelEvaluatorDefinition;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import java.util.Collection;
import java.util.List;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CapabilitiesService {
  private ExpressionsSupport expressionsSupport;

  @Autowired
  public CapabilitiesService(
      List<ExpressionFunctionProvider> expressionFunctionProviders,
      PluginManager pluginManager,
      ExpressionProperties expressionProperties) {
    this.expressionsSupport =
        new ExpressionsSupport(
            new Class[] {}, expressionFunctionProviders, pluginManager, expressionProperties);
  }

  public ExpressionCapabilityResult getExpressionCapabilities() {
    ExpressionCapabilityResult result = new ExpressionCapabilityResult();

    for (ExpressionFunctionProvider provider :
        expressionsSupport.getExpressionFunctionProviders()) {
      Collection<ExpressionFunctionProvider.FunctionDefinition> functions =
          provider.getFunctions().getFunctionsDefinitions();

      for (ExpressionFunctionProvider.FunctionDefinition function : functions) {
        result
            .getFunctions()
            .add(new ExpressionFunctionDefinition(provider.getNamespace(), function));
      }
    }

    for (PipelineExpressionEvaluator.SpelEvaluatorVersion spelVersion :
        PipelineExpressionEvaluator.SpelEvaluatorVersion.values()) {
      if (spelVersion.getIsSupported()) {
        result.getSpelEvaluators().add(new ExpressionSpelEvaluatorDefinition(spelVersion));
      }
    }

    return result;
  }
}
