/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Used to flag specific types and interfaces as a Public API.
 *
 * <p>Public APIs are Beans that can safely be used by Spinnaker Extensions without worrying about
 * backwards-incompatible changes across major service and library versions. Extensions are always
 * allowed to use types not flagged with PublicApi, but will not be provided the same compatibility
 * guarantees that PublicApi-annotated types will have.
 *
 * <p>This annotation should not be used with the Guava Beta annotation. Using the PublicApi
 * annotation signals that a particular type's contract is stable. If your type is planned to become
 * a PublicApi but is not stable yet, use the Guava Beta annotation and once you're satisfied with
 * the contract, replace it with this annotation.
 */
@Inherited
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicApi {
  /** @return Unused. */
  String value() default "";

  /** @return The `{major}.{minor}` service version when this PublicApi was exposed. */
  String since() default "";
}
