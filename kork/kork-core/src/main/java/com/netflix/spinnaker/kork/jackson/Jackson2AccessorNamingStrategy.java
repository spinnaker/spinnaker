/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.kork.jackson;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AccessorNamingStrategy;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy;

/** Preserves Jackson 2's lowercasing of leading capitals in bean accessor names. */
public final class Jackson2AccessorNamingStrategy extends AccessorNamingStrategy {
  private final AccessorNamingStrategy delegate;

  private Jackson2AccessorNamingStrategy(AccessorNamingStrategy delegate) {
    this.delegate = delegate;
  }

  @Override
  public String findNameForIsGetter(AnnotatedMethod method, String name) {
    return lowercaseLeadingCapitals(delegate.findNameForIsGetter(method, name));
  }

  @Override
  public String findNameForRegularGetter(AnnotatedMethod method, String name) {
    return lowercaseLeadingCapitals(delegate.findNameForRegularGetter(method, name));
  }

  @Override
  public String findNameForMutator(AnnotatedMethod method, String name) {
    return lowercaseLeadingCapitals(delegate.findNameForMutator(method, name));
  }

  @Override
  public String modifyFieldName(AnnotatedField field, String name) {
    return delegate.modifyFieldName(field, name);
  }

  private static String lowercaseLeadingCapitals(String name) {
    if (name == null || name.isEmpty()) {
      return name;
    }
    StringBuilder result = new StringBuilder(name);
    for (int i = 0; i < name.length(); i++) {
      char original = name.charAt(i);
      char lowercase = Character.toLowerCase(original);
      if (original == lowercase) {
        break;
      }
      result.setCharAt(i, lowercase);
    }
    return result.toString();
  }

  /** Uses Jackson's accessor discovery and builder prefixes; record names remain unchanged. */
  public static final class Provider extends DefaultAccessorNamingStrategy.Provider {
    public Provider() {
      super(
          "set",
          "with",
          "get",
          "is",
          DefaultAccessorNamingStrategy.FirstCharBasedValidator.forFirstNameRule(true, true));
    }

    @Override
    public AccessorNamingStrategy forPOJO(MapperConfig<?> config, AnnotatedClass targetClass) {
      return new Jackson2AccessorNamingStrategy(super.forPOJO(config, targetClass));
    }

    @Override
    public AccessorNamingStrategy forBuilder(
        MapperConfig<?> config, AnnotatedClass builderClass, BeanDescription valueType) {
      return new Jackson2AccessorNamingStrategy(super.forBuilder(config, builderClass, valueType));
    }
  }
}
