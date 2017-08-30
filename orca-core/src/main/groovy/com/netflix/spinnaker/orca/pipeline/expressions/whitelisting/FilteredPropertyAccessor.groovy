/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
