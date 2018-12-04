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

import com.netflix.spinnaker.keel.api.Asset
import kotlin.reflect.KClass

interface AssetPlugin : KeelPlugin {
  /**
   * Maps the kind (qualified CRD name) to the implementation type.
   */
  val supportedKinds: Map<String, KClass<out Any>>

  fun current(request: Asset<*>): CurrentResponse
  fun create(request: Asset<*>): ConvergeResponse = upsert(request)
  fun update(request: Asset<*>): ConvergeResponse = upsert(request)
  fun upsert(request: Asset<*>): ConvergeResponse {
    TODO("Not implemented")
  }

  fun delete(request: Asset<*>): ConvergeResponse
}

sealed class CurrentResponse

data class ResourceState<T : Any>(val spec: T) : CurrentResponse()

object ResourceMissing : CurrentResponse()

data class ResourceError(val reason: String) : CurrentResponse()

sealed class ConvergeResponse

object ConvergeAccepted : ConvergeResponse()

data class ConvergeFailed(val reason: String) : ConvergeResponse()
