/*
 *
 * Copyright 2019 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec

/**
 * Implementations apply changes to a [ResourceSpec] such as adding default values, applying
 * opinions, or resolving references.
 */
interface Resolver<T : ResourceSpec> : (Resource<T>) -> Resource<T> {
  val apiVersion: String
  val supportedKind: String
}

fun <T : ResourceSpec> Iterable<Resolver<*>>.supporting(
  resource: Resource<T>
): Iterable<Resolver<T>> =
  filter { it.apiVersion == resource.apiVersion && it.supportedKind == resource.kind }
    .filterIsInstance<Resolver<T>>()
