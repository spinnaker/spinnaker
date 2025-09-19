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

/**
 * Secret references are opaque pointers to external secret data which may be resolved by a
 * [SecretReferenceResolver].
 */
interface SecretReference {
  /**
   * Holds the URI scheme used by this secret reference.
   */
  val scheme: String

  /**
   * Holds the engine id that should be used for resolving secrets for this reference.
   */
  val engineIdentifier: String

  /**
   * Holds the URI of this secret reference.
   */
  val uri: String

  /**
   * Gets a parameter value for the given parameter if defined or `null` otherwise.
   */
  fun getParameter(parameter: SecretParameter): String?

  /**
   * Gets a parameter value for the given parameter if defined or throws an exception otherwise.
   *
   * @throws MissingSecretParameterException if no parameter is defined
   */
  fun getRequiredParameter(parameter: SecretParameter): String =
    getParameter(parameter) ?: throw MissingSecretParameterException(parameter)
}
