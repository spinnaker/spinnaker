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
package com.netflix.spinnaker.keel.grpc

import com.netflix.spinnaker.keel.api.Decision
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.processing.VetoService
import org.springframework.stereotype.Component

@Component
class GrpcVetoService(
  private val registry: GrpcPluginRegistry
) : VetoService {
  override fun allow(asset: AssetContainer): Boolean =
    registry.applyVetos { stub ->
      stub.allow(asset.asset!!.toProto())
    }.all { it.decision == Decision.proceed }
}
