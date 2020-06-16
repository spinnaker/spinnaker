/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.plugins.config

import com.fasterxml.jackson.core.type.TypeReference
import com.netflix.spinnaker.kork.annotations.Beta

/**
 * Resolves configuration values for plugins and their extensions.
 */
@Beta
interface ConfigResolver {
  /**
   * Resolve config by [coordinates], casting the resulting config into the [expectedType] if possible.
   *
   * Should the config not exist, reasonable defaults (defined by the [expectedType]'s default constructor)
   * must be used rather than throwing an error.
   */
  fun <T> resolve(coordinates: ConfigCoordinates, expectedType: Class<T>): T

  /**
   * @see [resolve].
   *
   * Instead of taking a concrete [Class], this method accepts Jackson's [TypeReference].
   *
   * IMPORTANT: [T] should be a concrete class, not a reference to an interface. For example, [HashMap] should be used
   * instead of [Map], so that a default can be returned should no config exist at the desired [coordinates].
   */
  fun <T> resolve(coordinates: ConfigCoordinates, expectedType: TypeReference<T>): T
}
