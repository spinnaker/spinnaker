package com.netflix.spinnaker.orca.pipeline.expressions.whitelisting

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.expression.spel.support.ReflectivePropertyAccessor

import java.lang.reflect.Field
import java.lang.reflect.Method

@CompileStatic
@Slf4j
class FilteredPropertyAccessor extends ReflectivePropertyAccessor {
  @Override
  protected Method findGetterForProperty(String propertyName, Class<?> clazz, boolean mustBeStatic) {
    Method getter = super.findGetterForProperty(propertyName, clazz, mustBeStatic)
    if (getter && ReturnTypeRestrictor.supports(getter.returnType)) {
      return getter
    } else if (getter && !ReturnTypeRestrictor.supports(getter.returnType)) {
      throw new IllegalArgumentException("found getter for requested $propertyName but rejected due to return type $getter.returnType")
    }

    throw new IllegalArgumentException("requested getter $propertyName not found on type  $clazz")
  }

  @Override
  protected Field findField(String name, Class<?> clazz, boolean mustBeStatic) {
    Field field = super.findField(name, clazz, mustBeStatic)
    if (field && ReturnTypeRestrictor.supports(field.type)) {
      return field
    } else if (field && !ReturnTypeRestrictor.supports(field.type)) {
      throw new IllegalArgumentException("found field $name but rejected due to unsupported type  $clazz")
    }

    throw new IllegalArgumentException("requested field $name not found on type $clazz")
  }
}
