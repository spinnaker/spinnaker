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
 * Represents a secret [URI][java.net.URI] type. The type of the URI determines what components are relevant to
 * parse when resolving secret URIs.
 */
enum class SecretUriType {
  /**
   * Opaque URIs have no slash `/` character after the [scheme][java.net.URI.getScheme]. The structure of the
   * URI is specified through the [scheme specific part][java.net.URI.getSchemeSpecificPart].
   */
  OPAQUE,

  /**
   * Hierarchical URIs begin with a `/` character after the [scheme][java.net.URI.getScheme].
   */
  HIERARCHICAL
}
