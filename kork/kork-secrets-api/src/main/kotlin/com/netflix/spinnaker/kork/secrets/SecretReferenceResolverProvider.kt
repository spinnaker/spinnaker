/*
 * Copyright 2024 Apple, Inc.
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

package com.netflix.spinnaker.kork.secrets

import org.springframework.core.env.Environment

/**
 * Spring factory interface for creating [SecretReferenceResolver] instances during bootstrap. Implementations should
 * add an entry to their `META-INF/spring.factories` file under the key
 * `com.netflix.spinnaker.kork.secrets.SecretReferenceResolverProvider`.
 */
interface SecretReferenceResolverProvider {
  /**
   * Creates a secret value resolver if enabled or `null` otherwise.
   *
   * @param environment input environment before any secret resolving has been applied
   * @return the configured secret resolver if enabled or null otherwise
   */
  fun create(environment: Environment): SecretReferenceResolver?
}
