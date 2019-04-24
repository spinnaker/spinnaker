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
   * This method is a no-op when the model and resolved types are the same.
   */
  override fun desired(resource: Resource<T>): T = resource.spec
}

/**
 * Searches a list of `ResourceHandler`s and returns the first that supports [apiVersion] and
 * [kind].
 *
 * @throws UnsupportedKind if no appropriate handlers are found in the list.
 */
internal fun List<ResourceHandler<*>>.supporting(
  apiVersion: ApiVersion,
  kind: String
): ResourceHandler<*> =
  find { it.apiVersion == apiVersion && it.supportedKind.first.singular == kind }
    ?: throw UnsupportedKind(apiVersion, kind)

internal class UnsupportedKind(apiVersion: ApiVersion, kind: String) :
  IllegalStateException("No resource handler supporting \"$kind\" in \"$apiVersion\" is available")
