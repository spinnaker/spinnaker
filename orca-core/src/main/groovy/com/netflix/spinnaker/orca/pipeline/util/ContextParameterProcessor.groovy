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
 * @author clin
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

  private static MapPropertyAccessor = new MapPropertyAccessor()

  private static ExpressionParser parser = new SpelExpressionParser()

  static Map process(Map parameters, Map context) {
    if (!parameters) {
      return null
    }

    transform(parameters, precomputeValues(context))
  }

  static Map precomputeValues(Map context){
    context.scmInfo = context.buildInfo?.scm ?: context.trigger?.buildInfo?.scm ?: null
    if(context.scmInfo && context.scmInfo.size() >= 2){
      context.scmInfo = context.scmInfo.find{ it.branch != 'master' && it.branch != 'develop' }
    } else {
      context.scmInfo = context.scmInfo?.first()
    }
    context
  }

  static def transform(parameters, context) {
    if (parameters instanceof Map) {
      return parameters.collectEntries { k, v ->
        [k, transform(v, context)]
      }
    } else if (parameters instanceof List) {
      return parameters.collect {
        transform(it, context)
      }
    } else if (parameters instanceof String || parameters instanceof GString) {
      String convertedValue = parameters
      EvaluationContext evaluationContext = new StandardEvaluationContext(context)
      evaluationContext.addPropertyAccessor(MapPropertyAccessor)
      evaluationContext.registerFunction('alphanumerical', ContextStringUtilities.getDeclaredMethod("alphanumerical", String))
      try {
        Expression exp = parser.parseExpression(parameters, parserContext)
        convertedValue = exp.getValue(evaluationContext)
      } catch (e) {
      }
      return convertedValue ?: parameters
    } else {
      return parameters
    }
  }

}

abstract class ContextStringUtilities {
  static String alphanumerical(String str) {
    str.replaceAll('[^A-Za-z0-9]', '')
  }
}

class MapPropertyAccessor extends ReflectivePropertyAccessor {

  public MapPropertyAccessor() {
    super()
  }

  @Override
  Class<?>[] getSpecificTargetClasses() {
    [Map]
  }

  @Override
  boolean canRead(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    true
  }

  @Override
  public TypedValue read(final EvaluationContext context, final Object target, final String name)
    throws AccessException {
    if (!(target instanceof Map)) {
      throw new AccessException("Cannot read target of class " + target.getClass().getName())
    }
    new TypedValue(((Map<String, ?>) target).get(name))
  }

}
