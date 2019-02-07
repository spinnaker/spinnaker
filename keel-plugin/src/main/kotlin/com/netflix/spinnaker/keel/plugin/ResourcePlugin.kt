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

interface ResourcePlugin : KeelPlugin {

  val apiVersion: ApiVersion

  /**
   * Maps the kind to the implementation type.
   */
  val supportedKinds: Map<ResourceKind, Class<out Any>>

  fun current(request: Resource<*>): CurrentResponse
  fun create(request: Resource<*>): ConvergeResponse = upsert(request)
  fun update(request: Resource<*>): ConvergeResponse = upsert(request)
  fun upsert(request: Resource<*>): ConvergeResponse {
    TODO("Not implemented")
  }

  fun delete(request: Resource<*>): ConvergeResponse
}

sealed class CurrentResponse

data class ResourceState<T : Any>(val spec: T) : CurrentResponse()

object ResourceMissing : CurrentResponse()

data class ResourceError(val reason: String) : CurrentResponse()

sealed class ConvergeResponse

object ConvergeAccepted : ConvergeResponse()

data class ConvergeFailed(val reason: String) : ConvergeResponse()
