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

interface ResourceHandler<T : Any> : ResolvableResourceHandler<T, T> {

  /**
   * Don't override this method, just implement [current]. If you need to do any resolution of the
   * desired value you should implement [ResolvableResourceHandler] instead of this interface.
   */
  override fun resolve(resource: Resource<T>): ResolvedResource<T> =
    ResolvedResource(resource.spec, current(resource))

  /**
   * Return the current _actual_ representation of what [resource] looks like in the cloud.
   * The entire desired state is passed so that implementations can use whatever identifying
   * information they need to look up the resource.
   *
   * Implementations of this method should not actuate any changes.
   */
  fun current(resource: Resource<T>): T?
}

/**
 * Searches a list of `ResourceHandler`s and returns the first that supports [apiVersion] and
 * [kind].
 *
 * @throws UnsupportedKind if no appropriate handlers are found in the list.
 */
fun List<ResolvableResourceHandler<*, *>>.supporting(
  apiVersion: ApiVersion,
  kind: String
): ResolvableResourceHandler<*, *> =
  find { it.apiVersion == apiVersion && it.supportedKind.first.singular == kind }
    ?: throw UnsupportedKind(apiVersion, kind)

internal class UnsupportedKind(apiVersion: ApiVersion, kind: String) :
  IllegalStateException("No resource handler supporting \"$kind\" in \"$apiVersion\" is available")
