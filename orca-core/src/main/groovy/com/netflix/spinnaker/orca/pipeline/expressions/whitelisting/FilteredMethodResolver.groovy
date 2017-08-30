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

import org.springframework.expression.spel.support.ReflectiveMethodResolver

import java.lang.reflect.Method

class FilteredMethodResolver extends ReflectiveMethodResolver {

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
    m = m.findAll { ReturnTypeRestrictor.supports(it.returnType) }

    return m.toArray(new Method[m.size()])
  }
}
