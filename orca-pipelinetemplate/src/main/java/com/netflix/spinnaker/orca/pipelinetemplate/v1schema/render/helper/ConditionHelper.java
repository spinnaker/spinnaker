/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.helper;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import static org.apache.commons.lang3.Validate.notNull;

public enum ConditionHelper implements Helper<Object> {

  /**
   * ```
   * {{isEqual value "otherValue"}}
   * ```
   *
   * Will return true if `value` is "otherValue"
   */
  isEqual {
    @Override
    public CharSequence apply(Object value, Options options) {
      return value.equals(options.param(0)) ? "true" : "false";
    }
  },

  /**
   * ```
   * {{isNotEqual value "otherValue"}}
   *
   * Will return true if `value` is not "otherValue"
   * ```
   */
  isNotEqual {
    @Override
    public CharSequence apply(Object value, Options options) {
      return value.equals(options.param(0)) ? "false" : "true";
    }
  },

  /**
   * ```
   * {{contains listOrMapValue "value"}}
   * ```
   *
   * If listOrMapValue is a list, will return true if "value" is an element of the list
   * If listOrMapValue is a map, will return true if "value" is a value in the list
   */
  contains {
    @Override
    public CharSequence apply(Object value, Options options) {
      if (value instanceof Collection) {
        return ((Collection) value).contains(options.param(0)) ? "true" : "false";
      }
      if (value instanceof Map) {
        return ((Map) value).values().contains(options.param(0)) ? "true" : "false";
      }
      throw new IllegalArgumentException("contains helper expects either a map or list, got " + value.getClass().getSimpleName());
    }
  },

  /**
   * ```
   * {{containsKey mapValue "value"}}
   * ```
   *
   * Will return true if mapValue contains a key equal to "value"
   */
  containsKey {
    @Override
    public Object apply(Object value, Options options) throws IOException {
      if (value instanceof Map) {
        return ((Map) value).keySet().contains(options.param(0)) ? "true" : "false";
      }
      throw new IllegalArgumentException("containsKey helper expects a map, got " + value.getClass().getSimpleName());
    }
  }

  ;

  private void registerHelper(final Handlebars handlebars) {
    handlebars.registerHelper(this.name(), this);
  }

  public static void register(final Handlebars handlebars) {
    notNull(handlebars, "A handlebars object is required");
    for (ConditionHelper helper : values()) {
      helper.registerHelper(handlebars);
    }
  }
}
