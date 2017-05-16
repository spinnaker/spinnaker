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

import java.lang.reflect.Method
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.config.UserConfiguredUrlRestrictions
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.PackageScope
import org.springframework.expression.*
import org.springframework.expression.common.TemplateParserContext
import org.springframework.expression.spel.SpelEvaluationException
import org.springframework.expression.spel.SpelMessage
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.ReflectiveMethodResolver
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.expression.spel.support.StandardTypeLocator

/**
 * Common methods for dealing with passing context parameters used by both Script and Jenkins stages
 */
class ContextParameterProcessor {

  // uses $ instead of  #
  private final ParserContext parserContext = new TemplateParserContext('${', '}')

  private final MapPropertyAccessor allowUnknownKeysAccessor = new MapPropertyAccessor(true)
  private final MapPropertyAccessor requireKeysAccessor = new MapPropertyAccessor(false)

  private final ExpressionParser parser = new SpelExpressionParser()

  private final ContextFunctionConfiguration contextFunctionConfiguration

  ContextParameterProcessor() {
    this(new ContextFunctionConfiguration(new UserConfiguredUrlRestrictions.Builder().build()))
  }

  ContextParameterProcessor(ContextFunctionConfiguration contextFunctionConfiguration) {
    this.contextFunctionConfiguration = contextFunctionConfiguration
    //this sucks so much:
    ContextUtilities.contextFunctionConfiguration.set(contextFunctionConfiguration)
  }

  Map<String, Object> process(Map<String, Object> parameters, Map<String, Object> context, boolean allowUnknownKeys) {
    if (!parameters) {
      return [:]
    }

    transform(parameters, precomputeValues(context), allowUnknownKeys)
  }

  Map<String, Object> buildExecutionContext(Stage stage, boolean includeStageContext) {
    def augmentedContext = [:] + (includeStageContext ? stage.context : [:])
    if (stage.execution instanceof Pipeline) {
      augmentedContext.put('trigger', ((Pipeline) stage.execution).trigger)
      augmentedContext.put('execution', stage.execution)
    }

    return augmentedContext
  }

  boolean containsExpression(String value) {
    return value?.contains(parserContext.getExpressionPrefix())
  }

  Map<String, Object> precomputeValues(Map<String, Object> context) {

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
        it.type in ['deploy', 'createServerGroup', 'cloneServerGroup', 'rollingPush'] && it.status == ExecutionStatus.SUCCEEDED
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

  protected <T> T transform(T parameters, Map<String, Object> context, boolean allowUnknownKeys) {
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
      StandardEvaluationContext evaluationContext = new StandardEvaluationContext(context)
      evaluationContext.setTypeLocator(new WhitelistTypeLocator())
      evaluationContext.setMethodResolvers([new FilteredMethodResolver()])
      evaluationContext.addPropertyAccessor(allowUnknownKeys ? allowUnknownKeysAccessor : requireKeysAccessor)

      evaluationContext.registerFunction('alphanumerical', ContextUtilities.getDeclaredMethod("alphanumerical", String))
      evaluationContext.registerFunction('toJson', ContextUtilities.getDeclaredMethod("toJson", Object))
      evaluationContext.registerFunction('readJson', ContextUtilities.getDeclaredMethod("readJson", String))
      evaluationContext.registerFunction('toInt', ContextUtilities.getDeclaredMethod("toInt", String))
      evaluationContext.registerFunction('toFloat', ContextUtilities.getDeclaredMethod("toFloat", String))
      evaluationContext.registerFunction('toBoolean', ContextUtilities.getDeclaredMethod("toBoolean", String))
      evaluationContext.registerFunction('toBase64', ContextUtilities.getDeclaredMethod("toBase64", String))
      evaluationContext.registerFunction('fromBase64', ContextUtilities.getDeclaredMethod("fromBase64", String))

      // only add methods that are context sensitive at stage evaluation time
      if (allowUnknownKeys) {
        evaluationContext.registerFunction('fromUrl', ContextUtilities.getDeclaredMethod("fromUrl", String))
        evaluationContext.registerFunction('jsonFromUrl', ContextUtilities.getDeclaredMethod("jsonFromUrl", String))
        evaluationContext.registerFunction('propertiesFromUrl', ContextUtilities.getDeclaredMethod("propertiesFromUrl", String))
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

  static class WhitelistTypeLocator implements TypeLocator {
    private final Set<Class<?>> allowedTypes = Collections.unmodifiableSet([
        String,
        Date,
        Integer,
        Long,
        Double,
        Byte,
        SimpleDateFormat,
        Math,
        Random,
        UUID,
        Boolean
    ] as Set)

    final TypeLocator delegate = new StandardTypeLocator()
    @Override
    Class<?> findType(String typeName) throws EvaluationException {
      def type = delegate.findType(typeName)
      if (allowedTypes.contains(type)) {
        return type
      }

      throw new SpelEvaluationException(SpelMessage.TYPE_NOT_FOUND, typeName)
    }
  }

  static class FilteredMethodResolver extends ReflectiveMethodResolver {

    private static final List<Method> rejectedMethods = buildRejectedMethods()

    private static List<Method> buildRejectedMethods() {
      def rejectedMethods = []
      def allowedObjectMethods = [
          Object.getMethod("equals", Object),
          Object.getMethod("hashCode"),
          Object.getMethod("toString")
      ]
      def objectMethods = new ArrayList<Method>(Arrays.asList(Object.getMethods()))
      objectMethods.removeAll(allowedObjectMethods)
      rejectedMethods.addAll(objectMethods)
      rejectedMethods.addAll(Class.getMethods())
      rejectedMethods.addAll(Boolean.getMethods().findAll { it.name == 'getBoolean' })
      rejectedMethods.addAll(Integer.getMethods().findAll { it.name == 'getInteger' })
      rejectedMethods.addAll(Long.getMethods().findAll { it.name == 'getLong' })

      return Collections.unmodifiableList(rejectedMethods)
    }

    @Override
    protected Method[] getMethods(Class<?> type) {
      Method[] methods = super.getMethods(type)

      def m = new ArrayList<Method>(Arrays.asList(methods))
      m.removeAll(rejectedMethods)

      return m.toArray(new Method[m.size()])
    }
  }
}


class ContextFunctionConfiguration {
  final UserConfiguredUrlRestrictions urlRestrictions

  ContextFunctionConfiguration(UserConfiguredUrlRestrictions urlRestrictions) {
    this.urlRestrictions = urlRestrictions
  }
}

abstract class ContextUtilities {

  @PackageScope static final AtomicReference<ContextFunctionConfiguration> contextFunctionConfiguration = new AtomicReference<>(new ContextFunctionConfiguration())

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
    URL u = contextFunctionConfiguration.get().urlRestrictions.validateURI(url).toURL()
    return u.getText()
  }

  static Object readJson(String text) {
    new ObjectMapper().readValue(text, text.startsWith('[') ? List : Map)
  }

  static Map propertiesFromUrl(String url) {
    readProperties(fromUrl(url))
  }

  static Map readProperties(String text) {
    Map map = [:]
    Properties properties = new Properties()
    properties.load(new ByteArrayInputStream(text.bytes))
    map.putAll(properties)
    map
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

  static String toBase64(String text) {
    Base64.getEncoder().encodeToString(text.getBytes())
  }

  static String fromBase64(String text) {
    new String(Base64.getDecoder().decode(text), 'UTF-8')
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
