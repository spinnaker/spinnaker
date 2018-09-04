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
package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.plugin.VetoPlugin
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService

/**
 * This is kind of the simplest veto plugin possible, using a configuration value to determine if convergence is
 * globally enabled.
 */
@GRpcService
class SimpleVetoPlugin(
  private val dynamicConfigService: DynamicConfigService
) : VetoPlugin() {

  override fun allow(request: Asset, responseObserver: StreamObserver<AllowResponse>) {
    with(responseObserver) {
      onNext(AllowResponse
        .newBuilder()
        .apply {
          decision = when (dynamicConfigService.isEnabled("keel.converge.enabled", false)) {
            true -> Decision.proceed
            false -> Decision.halt
          }
        }
        .build()
      )
      onCompleted()
    }
  }
}
