package com.netflix.spinnaker.keel.plugins

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.VetoPluginGrpc
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginResponse
import com.netflix.spinnaker.keel.api.engine.VetoPluginRegistryGrpc
import io.grpc.stub.StreamObserver
import org.slf4j.LoggerFactory

class VetoPluginRegistry(
  override val eurekaClient: EurekaClient
) : VetoPluginRegistryGrpc.VetoPluginRegistryImplBase(), Registry<VetoPluginGrpc.VetoPluginBlockingStub> {

  private val log = LoggerFactory.getLogger(javaClass)
  private val vetoPlugins: MutableSet<String> = mutableSetOf()
  override val stubFactory = VetoPluginGrpc::newBlockingStub

  fun <R> applyVetos(callback: (VetoPluginGrpc.VetoPluginBlockingStub) -> R): Iterable<R> =
    vetoPlugins.map(this::stubFor).map(callback)

  override fun register(
    request: RegisterVetoPluginRequest,
    responseObserver: StreamObserver<RegisterVetoPluginResponse>
  ) {
    vetoPlugins.add(request.name)
    log.info("Registered veto plugin \"${request.name}\"")
    responseObserver.apply {
      onNext(
        RegisterVetoPluginResponse.newBuilder()
          .apply { succeeded = true }
          .build()
      )
      onCompleted()
    }
  }
}
