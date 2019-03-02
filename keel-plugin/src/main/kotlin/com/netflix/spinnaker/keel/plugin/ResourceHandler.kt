/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import de.danielbechler.diff.node.DiffNode

interface ResourceHandler<T : Any> : KeelPlugin {

  val apiVersion: ApiVersion

  /**
   * Maps the kind to the implementation type.
   */
  val supportedKind: Pair<ResourceKind, Class<T>>

  /**
   * Validates the resource, and constructs a name based on conventions
   */
  fun validate(resource: Resource<*>, generateName: Boolean): Resource<T>

  /**
   * Return the current _actual_ representation of what [resource] looks like in the cloud.
   * The entire desired state is passed so that implementations can use whatever identifying
   * information they need to look up the resource. Implementations of this method should not
   * actuate any changes.
   */
  fun current(resource: Resource<T>): T?

  /**
   * Create a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [update] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   */
  fun create(resource: Resource<T>) {
    upsert(resource, null)
  }

  /**
   * Update a resource so that it matches the desired state represented by [resource].
   *
   * By default this just delegates to [upsert].
   *
   * Implement this method and [create] if you need to handle create and update in different ways.
   * Otherwise just implement [upsert].
   */
  fun update(resource: Resource<T>, diff: DiffNode = DiffNode.newRootNode()) {
    upsert(resource, diff)
  }

  /**
   * Create or update a resource so that it matches the desired state represented by [resource].
   *
   * You don't need to implement this method if you are implementing [create] and [update]
   * individually.
   */
  fun upsert(resource: Resource<T>, diff: DiffNode? = null) {
    TODO("Not implemented")
  }

  /**
   * Delete a resource as the desired state is that it should no longer exist.
   */
  fun delete(resource: Resource<T>)
}
