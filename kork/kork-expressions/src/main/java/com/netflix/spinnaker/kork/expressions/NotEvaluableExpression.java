/*
 * Copyright 2022 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.kork.expressions;

/**
 * An instance of this class is used as the return value from the {@code doNotEval} SpEL helper
 * which makes it possible to skip SpEL evaluation in other SpEL helpers e.g. {@code toJson}.
 *
 * <p>For example, in the evaluation context is defined only {@code fileMap} object:
 *
 * <pre>{@code
 * Map<String, Object> fileMap = Collections.singletonMap("owner", "managed-by-${team}");
 * }</pre>
 *
 * <p>An exception will be thrown in attempt to get JSON because of {@code fileMap} contains SpEL
 * inside.
 *
 * <pre>{@code
 * ${#toJson(fileMap)}
 * }</pre>
 *
 * <p>In the given case {@code fileMap} contains SpEL for another tool e.g. Terraform. Use {@code
 * doNotEval} to let Spinnaker know that this SpEL should be evaluated by a different tool. No
 * exceptions are thrown this way.
 *
 * <pre>{@code
 * ${#toJson(#doNotEval(fileMap))}
 * }</pre>
 */
public class NotEvaluableExpression {

  private final Object expression;

  public NotEvaluableExpression(Object expression) {
    this.expression = expression;
  }

  public Object getExpression() {
    return expression;
  }
}
