/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.expression.AccessException
import org.springframework.expression.EvaluationContext
import org.springframework.expression.Expression
import org.springframework.expression.ExpressionParser
import org.springframework.expression.ParserContext
import org.springframework.expression.TypedValue
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.expression.spel.support.StandardEvaluationContext

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */
class ContextParameterProcessor {

  // uses $ instead of  #
  private static ParserContext parserContext = [
    getExpressionPrefix: {
      '${'
    },
    getExpressionSuffix: {
      '}'
    },
    isTemplate         : {
      true
    }
  ] as ParserContext

  private static final MapPropertyAccessor allowUnknownKeysAccessor = new MapPropertyAccessor(true)
  private static final MapPropertyAccessor requireKeysAccessor = new MapPropertyAccessor(false)

  private static ExpressionParser parser = new SpelExpressionParser()

  static Map process(Map parameters, Map context, boolean allowUnknownKeys) {
    if (!parameters) {
      return null
    }

    transform(parameters, precomputeValues(context), allowUnknownKeys)
  }

  static Map<String, Object> buildExecutionContext(Stage stage, boolean includeStageContext) {
    def augmentedContext = [:] + (includeStageContext ? stage.context : [:])
    if (stage.execution instanceof Pipeline) {
      augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
      augmentedContext.put('execution', stage.execution)
    }

    return augmentedContext
  }

  static boolean containsExpression(String value) {
    return value?.contains(parserContext.getExpressionPrefix())
  }

  static Map precomputeValues(Map context) {

    if (context.trigger?.parameters) {
      context.parameters = context.trigger.parameters
    }

    context.scmInfo = context.buildInfo?.scm ?: context.trigger?.buildInfo?.scm ?: null
    if (context.scmInfo && context.scmInfo.size() >= 2) {
      def scmInfo = context.scmInfo.find { it.branch != 'master' && it.branch != 'develop' }
      context.scmInfo = scmInfo ?: context.scmInfo?.first()
    } else {
      context.scmInfo = context.scmInfo?.first()
    }

    if (context.execution) {
      def deployedServerGroups = []
      context.execution.stages.findAll {
        it.type in ['deploy', 'cloneServerGroup', 'rollingPush'] && it.status == ExecutionStatus.SUCCEEDED
      }.each { deployStage ->
        if (deployStage.context.'deploy.server.groups') {
          Map deployDetails = [
            account    : deployStage.context.account,
            capacity   : deployStage.context.capacity,
            parentStage: deployStage.parentStageId,
            region     : deployStage.context.region ?: deployStage.context.availabilityZones.keySet().first(),
          ]
          deployDetails.putAll(context.execution?.context?.deploymentDetails?.find { it.region == deployDetails.region } ?: [:])
          deployDetails.serverGroup = deployStage.context.'deploy.server.groups'."${deployDetails.region}".first()
          deployedServerGroups << deployDetails
        }
      }
      if (!deployedServerGroups.empty) {
        context.deployedServerGroups = deployedServerGroups
      }
    }
    context
  }

  static <T> T transform(T parameters, Map context, boolean allowUnknownKeys) {
    if (parameters instanceof Map) {
      return parameters.collectEntries { k, v ->
        [transform(k, context, allowUnknownKeys), transform(v, context, allowUnknownKeys)]
      }
    } else if (parameters instanceof List) {
      return parameters.collect {
        transform(it, context, allowUnknownKeys)
      }
    } else if (parameters instanceof String || parameters instanceof GString) {
      Object convertedValue = parameters.toString()
      EvaluationContext evaluationContext = new StandardEvaluationContext(context)
      evaluationContext.addPropertyAccessor(allowUnknownKeys ? allowUnknownKeysAccessor : requireKeysAccessor)

      evaluationContext.registerFunction('alphanumerical', ContextUtilities.getDeclaredMethod("alphanumerical", String))
      evaluationContext.registerFunction('toJson', ContextUtilities.getDeclaredMethod("toJson", Object))
      evaluationContext.registerFunction('readJson', ContextUtilities.getDeclaredMethod("readJson", String))
      evaluationContext.registerFunction('toInt', ContextUtilities.getDeclaredMethod("toInt", String))
      evaluationContext.registerFunction('toFloat', ContextUtilities.getDeclaredMethod("toFloat", String))
      evaluationContext.registerFunction('toBoolean', ContextUtilities.getDeclaredMethod("toBoolean", String))

      // only add methods that are context sensitive at stage evaluation time
      if(allowUnknownKeys) {
        evaluationContext.registerFunction('fromUrl', ContextUtilities.getDeclaredMethod("fromUrl", String))
        evaluationContext.registerFunction('jsonFromUrl', ContextUtilities.getDeclaredMethod("jsonFromUrl", String))
        evaluationContext.registerFunction('stage', ContextUtilities.getDeclaredMethod("stage", Object, String))
        evaluationContext.registerFunction('judgment', ContextUtilities.getDeclaredMethod("judgment", Object, String))

        ["judgment", "stage"].each { contextAwareStageFunction ->
          if (convertedValue.contains("#${contextAwareStageFunction}(") && !convertedValue.contains("#${contextAwareStageFunction}( #root.execution, ")) {
            convertedValue = convertedValue.replaceAll("#${contextAwareStageFunction}\\(", "#${contextAwareStageFunction}( #root.execution, ")
          }
        }
      }

      try {
        Expression exp = parser.parseExpression(convertedValue, parserContext)
        convertedValue = exp.getValue(evaluationContext)
      } catch (e) {
        convertedValue = parameters
      }

      if (convertedValue == null) {
        convertedValue = parameters
      }

      return convertedValue
    } else {
      return parameters
    }
  }

}

abstract class ContextUtilities {

  static String alphanumerical(String str) {
    str.replaceAll('[^A-Za-z0-9]', '')
  }

  static String toJson(Object o) {
    new ObjectMapper().writeValueAsString(o)
  }

  static Integer toInt(String str) {
    Integer.valueOf(str)
  }

  static Float toFloat(String str) {
    Float.valueOf(str)
  }

  static Boolean toBoolean(String str) {
    Boolean.valueOf(str)
  }

  static String fromUrl(String url) {
    new URL(url).text
  }

  static Object readJson(String text) {
    new ObjectMapper().readValue(text, text.startsWith('[') ? List : Map)
  }

  static Object jsonFromUrl(String url) {
    readJson(fromUrl(url))
  }

  static Object stage(Object context, String id) {
    context.stages?.find { it.name == id }
  }

  static String judgment(Object context, String id) {
    context.stages?.find { it.name == id && it.type == 'manualJudgment' }?.context?.judgmentInput
  }

}

class MapPropertyAccessor extends ReflectivePropertyAccessor {
  private final boolean allowUnknownKeys

  public MapPropertyAccessor(boolean allowUnknownKeys) {
    super()
    this.allowUnknownKeys = allowUnknownKeys
  }

  @Override
  Class<?>[] getSpecificTargetClasses() {
    [Map]
  }

  @Override
  boolean canRead(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    if (target instanceof Map) {
      return allowUnknownKeys || target.containsKey(name)
    }
    return false
  }

  @Override
  public TypedValue read(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    if (target instanceof Map) {
      if (target.containsKey(name)) {
        return new TypedValue(target.get(name))
      } else if (allowUnknownKeys) {
        return TypedValue.NULL
      }
      throw new AccessException("No property in map with key $name")
    }
    throw new AccessException("Cannot read target of class " + target.getClass().getName())
  }
}
