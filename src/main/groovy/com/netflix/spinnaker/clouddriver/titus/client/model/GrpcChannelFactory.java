package com.netflix.spinnaker.clouddriver.titus.client.model;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.TitusRegion;
import io.grpc.ManagedChannel;

public interface GrpcChannelFactory {
  public ManagedChannel build(
    TitusRegion titusRegion,
    String environment,
    String eurekaName,
    long defaultConnectTimeOut,
    Registry registry
  );
}
