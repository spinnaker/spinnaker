/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.annotations;

import static java.lang.annotation.ElementType.*;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Provides additional deprecation information, which should be used alongside {@link Deprecated}.
 *
 * <p>For guidance and process of deprecations, please refer to spinnaker.io's "Managing
 * Deprecations" page: https://spinnaker.io/community/contributing/managing-deprecations/
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target(value = {CONSTRUCTOR, FIELD, LOCAL_VARIABLE, METHOD, PACKAGE, PARAMETER, TYPE})
public @interface DeprecationInfo {
  /** Constant value for {@link DeprecationInfo#replacedBy}. */
  String NO_REPLACEMENT = "noReplacement";

  /**
   * A short explanation of the deprecation.
   *
   * <p>If a short explanation is insufficient, use {@link DeprecationInfo#link()} to provide
   * additional context.
   *
   * <p>Example: "Use of Duration is preferred over primitive long values."
   */
  String reason();

  /**
   * The product version that the deprecation was introduced.
   *
   * <p>Example: "1.20.0"
   */
  String since();

  /**
   * The scheduled product version that will see this functionality removed from the codebase.
   *
   * <p>Example: "1.25.0"
   */
  String eol();

  /**
   * Short explanation on what functionality is replacing this deprecation. If there is no
   * replacement, {@link DeprecationInfo#NO_REPLACEMENT} should be used.
   *
   * <p>If a short explanation is insufficient, use {@link DeprecationInfo#link()} to provide
   * additional context.
   *
   * <p>Example: "{@code MyClass#theMethodReturningDuration()}"
   */
  String replaceWith() default NO_REPLACEMENT;

  /** An optional link to the Github issue, PR or document that covers this deprecation. */
  String link() default "";
}
