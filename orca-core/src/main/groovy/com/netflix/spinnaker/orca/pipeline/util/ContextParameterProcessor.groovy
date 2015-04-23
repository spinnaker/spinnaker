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
    isTemplate : {
     true
    }
  ] as ParserContext

  private static MapPropertyAccessor = new MapPropertyAccessor()

  private static ExpressionParser parser = new SpelExpressionParser()

  static Map process(Map parameters, Map context) {
    if (!parameters) {
      return null
    }

    EvaluationContext evaluationContext = new StandardEvaluationContext(context)
    evaluationContext.addPropertyAccessor(MapPropertyAccessor)

    parameters.collectEntries { k, v ->
      String convertedValue = v
      try {
        Expression exp = parser.parseExpression(v, parserContext)
        convertedValue = exp.getValue(evaluationContext)
      }catch(e){
      }
      [k, convertedValue?:v]
    }
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
    new TypedValue(((Map<String,?>)target).get(name))
  }

}
