/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.titus.client;

import com.google.protobuf.Empty;
import com.netflix.eureka2.grpc.nameresolver.Eureka2NameResolverFactory;
import com.netflix.grpc.interceptor.spectator.SpectatorMetricsClientInterceptor;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.v3client.ClientAuthenticationUtils;
import com.netflix.spinnaker.clouddriver.titus.v3client.GrpcMetricsInterceptor;
import com.netflix.spinnaker.clouddriver.titus.v3client.GrpcRetryInterceptor;
import com.netflix.titus.grpc.protogen.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.NegotiationType;
import io.grpc.netty.NettyChannelBuilder;
import io.grpc.util.RoundRobinLoadBalancerFactory;

import java.util.List;

public class RegionScopedTitusAutoscalingClient implements TitusAutoscalingClient {

  /**
   * Default connect timeout in milliseconds
   */
  private static final long DEFAULT_CONNECT_TIMEOUT = 5000;

  private final AutoScalingServiceGrpc.AutoScalingServiceBlockingStub autoScalingServiceBlockingStub;

  public RegionScopedTitusAutoscalingClient(TitusRegion titusRegion,
                                            Registry registry,
                                            String environment,
                                            String eurekaName) {

    ManagedChannel eurekaChannel = NettyChannelBuilder
      .forTarget("eurekaproxy." + titusRegion.getName() + ".discovery" + environment + ".netflix.net:8980")
      .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
      .usePlaintext(true)
      .userAgent("spinnaker")
      .build();

    ManagedChannel channel = NettyChannelBuilder
      .forTarget("eureka:///" + eurekaName + "?eureka.status=up")
      .sslContext(ClientAuthenticationUtils.newSslContext("titusapi"))
      .negotiationType(NegotiationType.TLS)
      .nameResolverFactory(new Eureka2NameResolverFactory(eurekaChannel)) // This enables the client to resolve the Eureka URI above into a set of addressable service endpoints.
      .loadBalancerFactory(RoundRobinLoadBalancerFactory.getInstance())
      .intercept(new GrpcMetricsInterceptor(registry, titusRegion))
      .intercept(new GrpcRetryInterceptor(DEFAULT_CONNECT_TIMEOUT))
      .intercept(new SpectatorMetricsClientInterceptor(registry))
      .build();

    this.autoScalingServiceBlockingStub = AutoScalingServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public List<ScalingPolicyResult> getAllScalingPolicies() {
    return autoScalingServiceBlockingStub.getAllScalingPolicies(Empty.newBuilder().build()).getItemsList();
  }

  @Override
  public List<ScalingPolicyResult> getJobScalingPolicies(String jobId) {
    JobId request = JobId.newBuilder().setId(jobId).build();
    return autoScalingServiceBlockingStub
      .getJobScalingPolicies(request).getItemsList();
  }

  @Override
  public ScalingPolicyID upsertScalingPolicy(PutPolicyRequest policy) {
    return autoScalingServiceBlockingStub
      .setAutoScalingPolicy(policy);
  }

  @Override
  public void deleteScalingPolicy(DeletePolicyRequest request) {
    autoScalingServiceBlockingStub.deleteAutoScalingPolicy(request);
  }

}
