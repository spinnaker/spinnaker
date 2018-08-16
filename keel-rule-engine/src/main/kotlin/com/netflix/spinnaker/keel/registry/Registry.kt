package com.netflix.spinnaker.keel.registry

import com.netflix.discovery.EurekaClient
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.stub.AbstractStub

interface Registry<T : AbstractStub<T>> {

  val eurekaClient: EurekaClient
  val stubFactory: (ManagedChannel) -> T

  fun stubFor(name: String): T =
    eurekaClient
      .getNextServerFromEureka(name, false)
      .let { address ->
        ManagedChannelBuilder
          .forAddress(address.ipAddr, address.port)
          .usePlaintext()
          .build()
          .let(stubFactory)
      }
}
