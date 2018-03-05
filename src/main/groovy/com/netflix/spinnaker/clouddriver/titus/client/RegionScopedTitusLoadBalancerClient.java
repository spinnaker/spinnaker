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

import com.netflix.eureka2.grpc.nameresolver.Eureka2NameResolverFactory;
import com.netflix.grpc.interceptor.spectator.SpectatorMetricsClientInterceptor;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.v3client.ClientAuthenticationUtils;
import com.netflix.spinnaker.clouddriver.titus.v3client.GrpcMetricsInterceptor;
import com.netflix.spinnaker.clouddriver.titus.v3client.GrpcRetryInterceptor;
import com.netflix.titus.grpc.protogen.*;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.util.RoundRobinLoadBalancerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionScopedTitusLoadBalancerClient implements TitusLoadBalancerClient {

  /**
   * Default connect timeout in milliseconds
   */
  private static final long DEFAULT_CONNECT_TIMEOUT = 60000;

  private final LoadBalancerServiceGrpc.LoadBalancerServiceBlockingStub loadBalancerServiceBlockingStub;

  public RegionScopedTitusLoadBalancerClient(TitusRegion titusRegion,
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
      .intercept(new GrpcRetryInterceptor(DEFAULT_CONNECT_TIMEOUT, titusRegion))
      .intercept(new SpectatorMetricsClientInterceptor(registry))
      .build();

    this.loadBalancerServiceBlockingStub = LoadBalancerServiceGrpc.newBlockingStub(channel);
  }

  @Override
  public List<LoadBalancerId> getJobLoadBalancers(String jobId) {
    return loadBalancerServiceBlockingStub.getJobLoadBalancers(JobId.newBuilder().setId(jobId).build()).getLoadBalancersList();
  }

  @Override
  public void addLoadBalancer(String jobId, String loadBalancerId) {
    loadBalancerServiceBlockingStub.addLoadBalancer(AddLoadBalancerRequest.newBuilder().setJobId(jobId).setLoadBalancerId(LoadBalancerId.newBuilder().setId(loadBalancerId).build()).build());
  }

  @Override
  public void removeLoadBalancer(String jobId, String loadBalancerId) {
    loadBalancerServiceBlockingStub.removeLoadBalancer(RemoveLoadBalancerRequest.newBuilder().setJobId(jobId).setLoadBalancerId(LoadBalancerId.newBuilder().setId(loadBalancerId).build()).build());
  }

  public Map<String, List<String>> getAllLoadBalancers() {
    Map<String, List<String>> results = new HashMap<>();
    for (GetJobLoadBalancersResult result : loadBalancerServiceBlockingStub.getAllLoadBalancers(GetAllLoadBalancersRequest.newBuilder().setPage(Page.newBuilder().setPageSize(1000).build()).build()).getJobLoadBalancersList()) {
      for (LoadBalancerId loadBalancerid : result.getLoadBalancersList()) {
        if(results.get(result.getJobId()) == null){
          List<String> loadBalancers = new ArrayList<>();
          loadBalancers.add(loadBalancerid.getId());
          results.put(result.getJobId(), loadBalancers);
        } else {
          results.get(result.getJobId()).add(loadBalancerid.getId());
        }
      }
    }
    return results;
  }

}
