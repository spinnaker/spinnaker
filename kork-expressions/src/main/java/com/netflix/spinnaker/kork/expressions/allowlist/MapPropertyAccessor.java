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

package com.netflix.spinnaker.kork.expressions.allowlist;

import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.TypedValue;

public class MapPropertyAccessor extends MapAccessor {
  private final boolean allowUnknownKeys;

  public MapPropertyAccessor(boolean allowUnknownKeys) {
    this.allowUnknownKeys = allowUnknownKeys;
  }

  @Override
  public boolean canRead(final EvaluationContext context, final Object target, final String name)
      throws AccessException {
    if (allowUnknownKeys) {
      return true;
    }
    return super.canRead(context, target, name);
  }

  @Override
  public TypedValue read(final EvaluationContext context, final Object target, final String name)
      throws AccessException {
    try {
      return super.read(context, target, name);
    } catch (AccessException ae) {
      if (allowUnknownKeys) {
        return TypedValue.NULL;
      }
      throw ae;
    }
  }
}
