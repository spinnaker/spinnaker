/*
 * Copyright 2026 Netflix, Inc.
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

package com.netflix.spinnaker.kork.expressions;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SpEL tokenizes {@code -} as the subtraction operator, so a template expression like {@code
 * ${my-container-name}} is parsed as {@code my - container - name} instead of a single literal
 * identifier. This resolver recognizes the narrow, unambiguous case where a whole {@code ${...}}
 * template expression is nothing but a hyphen-joined bareword, and rewrites it into SpEL's bracket
 * indexer syntax (e.g. {@code ${#root['my-container-name']}}), which resolves the hyphenated text
 * as a literal map-key lookup instead of letting SpEL interpret the hyphens as operators.
 *
 * <p>This intentionally does not attempt to rewrite anything else - in particular, real arithmetic
 * (e.g. {@code ${5 - 2}}, {@code ${trigger.buildNumber - 1}}) and compound property chains with a
 * hyphenated segment (e.g. {@code ${metadata.labels.app-name}}) are left untouched, to avoid
 * misinterpreting legitimate SpEL expressions.
 */
public final class DashedIdentifierResolver {

  private static final Pattern TEMPLATE_PATTERN =
      Pattern.compile("^\\$\\{\\s*(.+?)\\s*}$", Pattern.DOTALL);

  private static final Pattern DASHED_BAREWORD_PATTERN =
      Pattern.compile("^[A-Za-z_$][\\w$]*(-[A-Za-z_$][\\w$]*)+$");

  private DashedIdentifierResolver() {}

  /**
   * @param templateExpression the raw, unevaluated expression, including the {@code ${...}}
   *     template delimiters
   * @return a rewritten template expression that resolves the hyphenated text as a literal key
   *     lookup, or {@link Optional#empty()} if {@code templateExpression} isn't a single template
   *     expression whose entire body is a hyphen-joined bareword
   */
  public static Optional<String> rewriteHyphenatedBareword(String templateExpression) {
    if (templateExpression == null) {
      return Optional.empty();
    }

    Matcher templateMatcher = TEMPLATE_PATTERN.matcher(templateExpression.trim());
    if (!templateMatcher.matches()) {
      return Optional.empty();
    }

    String body = templateMatcher.group(1);
    if (!DASHED_BAREWORD_PATTERN.matcher(body).matches()) {
      return Optional.empty();
    }

    return Optional.of(String.format("${#root['%s']}", body));
  }
}
