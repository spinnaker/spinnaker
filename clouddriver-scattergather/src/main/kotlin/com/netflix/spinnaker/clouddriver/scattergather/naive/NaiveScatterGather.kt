/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather.naive

import com.netflix.spinnaker.clouddriver.scattergather.ReducedResponse
import com.netflix.spinnaker.clouddriver.scattergather.ResponseReducer
import com.netflix.spinnaker.clouddriver.scattergather.ScatterGather
import com.netflix.spinnaker.clouddriver.scattergather.ServletScatterGatherRequest
import com.netflix.spinnaker.clouddriver.scattergather.client.ScatteredOkHttpCallFactory
import java.util.UUID

/**
 * Performs a scatter/gather operation sequentially.
 *
 * This should be used only for development purposes, as it'll be crazy slow.
 * An async implementation should be used for non-development purposes.
 *
 * TODO(rz): Add CoroutinesScatterGather
 */
class NaiveScatterGather(
  private val callFactory: ScatteredOkHttpCallFactory
) : ScatterGather {
  override fun request(request: ServletScatterGatherRequest, reducer: ResponseReducer): ReducedResponse {
    val calls = callFactory.createCalls(
      UUID.randomUUID().toString(),
      request.targets,
      request.original
    )
    return reducer.reduce(calls.map { it.execute() })
  }
}
