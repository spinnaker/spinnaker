package com.netflix.spinnaker.clouddriver.titus.v3client

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class SimpleGrpcChannelFactory implements GrpcChannelFactory{
  @Override
  ManagedChannel build(TitusRegion titusRegion, String environment, String eurekaName, long defaultConnectTimeOut, Registry registry) {
    return ManagedChannelBuilder.forAddress(titusRegion.endpoint, 7104).usePlaintext(true).build();
  }
}
