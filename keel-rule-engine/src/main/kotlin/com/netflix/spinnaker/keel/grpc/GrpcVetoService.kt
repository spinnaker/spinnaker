package com.netflix.spinnaker.keel.grpc

import com.netflix.spinnaker.keel.api.Decision
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.processing.VetoService
import com.netflix.spinnaker.keel.registry.GrpcPluginRegistry
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
