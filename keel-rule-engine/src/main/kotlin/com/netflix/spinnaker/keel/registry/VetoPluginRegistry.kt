package com.netflix.spinnaker.keel.registry

import com.netflix.spinnaker.keel.api.VetoPluginGrpc.VetoPluginBlockingStub

interface VetoPluginRegistry {
  fun <R> applyVetos(
    // TODO: more abstract type that's not directly gRPC related
    callback: (VetoPluginBlockingStub) -> R
  ): Iterable<R>
}
