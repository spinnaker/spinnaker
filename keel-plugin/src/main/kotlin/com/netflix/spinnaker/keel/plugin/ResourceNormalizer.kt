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

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource

/**
 * A resource normalizer throws a [FailedValidationException] when a resource is invalid
 */
interface ResourceNormalizer<T : Any> {

  val apiVersion: ApiVersion
  val supportedKind: String

  fun validate(resource: Resource<*>): Resource<T>

}

internal fun ResourceNormalizer<*>.handles(
    apiVersion: ApiVersion,
    kind: String
): Boolean = this.apiVersion == apiVersion && this.supportedKind == kind
