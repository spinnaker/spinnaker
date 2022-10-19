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
 */

package com.netflix.spinnaker.orca.capabilities

import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties
import com.netflix.spinnaker.orca.capabilities.models.ExpressionCapabilityResult
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluatorSpec
import org.pf4j.PluginManager
import spock.lang.Specification

class CapabilitiesServiceSpec extends Specification {
  PluginManager pluginManager = Mock() {
    getExtensions(_) >> []
  }

  ExpressionProperties expressionProperties = Mock() {
    ExpressionProperties.FeatureFlag featureFlag = new ExpressionProperties.FeatureFlag()
    featureFlag.setEnabled(false) // arbitrary, the tests here don't care
    getDoNotEvalSpel() >> featureFlag
  }

  def 'should return valid data'() {
    given:
    def functionProvider = PipelineExpressionEvaluatorSpec.buildExpressionFunctionProvider("TEST_EXPRESSION_FUNCTION")
    def supportedSpelEvaluators = PipelineExpressionEvaluator.SpelEvaluatorVersion.values().findAll({
      it.isSupported
    }).collect({ it.key })

    CapabilitiesService capabilitiesService = new CapabilitiesService(Collections.singletonList(functionProvider), pluginManager, expressionProperties)

    when:
    ExpressionCapabilityResult capabilities = capabilitiesService.getExpressionCapabilities()

    then:
    def funcCaps = capabilities.functions.findAll({ it.name.contains("TEST_EXPRESSION_FUNCTION") })
    funcCaps.size() == 2
    funcCaps[0].name == 'functionWithExecutionContext-TEST_EXPRESSION_FUNCTION'
    funcCaps[0].description == 'description for: functionWithExecutionContext-TEST_EXPRESSION_FUNCTION'
    funcCaps[0].parameters.size() == 1
    funcCaps[0].parameters[0].type == 'String'
    funcCaps[0].parameters[0].name == 'someArg'
    funcCaps[0].parameters[0].description == 'A valid stage reference identifier'

    funcCaps[1].name == 'functionWithNoExecutionContext-TEST_EXPRESSION_FUNCTION'
    funcCaps[1].description == 'description for: functionWithNoExecutionContext-TEST_EXPRESSION_FUNCTION'
    funcCaps[1].parameters.size() == 1

    def spelVersions = capabilities.spelEvaluators.collect({ it.versionKey })
    spelVersions == supportedSpelEvaluators
    spelVersions.contains(PipelineExpressionEvaluator.SpelEvaluatorVersion.V3.key)
    spelVersions.contains(PipelineExpressionEvaluator.SpelEvaluatorVersion.V4.key)
  }
}
