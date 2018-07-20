package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.springframework.stereotype.Component

/**
 * This is kind of the simplest veto plugin possible, using a configuration value to determine if convergence is
 * globally enabled.
 */
@GRpcService
class SimpleVetoPlugin(
  private val dynamicConfigService: DynamicConfigService
) : VetoPluginGrpc.VetoPluginImplBase() {

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
