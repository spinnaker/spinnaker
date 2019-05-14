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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory;
import com.netflix.titus.grpc.protogen.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RegionScopedTitusLoadBalancerClient implements TitusLoadBalancerClient {

  /** Default connect timeout in milliseconds */
  private static final long DEFAULT_CONNECT_TIMEOUT = 60000;

  private final LoadBalancerServiceGrpc.LoadBalancerServiceBlockingStub
      loadBalancerServiceBlockingStub;

  public RegionScopedTitusLoadBalancerClient(
      TitusRegion titusRegion,
      Registry registry,
      String environment,
      String eurekaName,
      GrpcChannelFactory channelFactory) {
    this.loadBalancerServiceBlockingStub =
        LoadBalancerServiceGrpc.newBlockingStub(
            channelFactory.build(
                titusRegion, environment, eurekaName, DEFAULT_CONNECT_TIMEOUT, registry));
  }

  @Override
  public List<LoadBalancerId> getJobLoadBalancers(String jobId) {
    return loadBalancerServiceBlockingStub
        .getJobLoadBalancers(JobId.newBuilder().setId(jobId).build())
        .getLoadBalancersList();
  }

  @Override
  public void addLoadBalancer(String jobId, String loadBalancerId) {
    TitusClientAuthenticationUtil.attachCaller(loadBalancerServiceBlockingStub)
        .addLoadBalancer(
            AddLoadBalancerRequest.newBuilder()
                .setJobId(jobId)
                .setLoadBalancerId(LoadBalancerId.newBuilder().setId(loadBalancerId).build())
                .build());
  }

  @Override
  public void removeLoadBalancer(String jobId, String loadBalancerId) {
    TitusClientAuthenticationUtil.attachCaller(loadBalancerServiceBlockingStub)
        .removeLoadBalancer(
            RemoveLoadBalancerRequest.newBuilder()
                .setJobId(jobId)
                .setLoadBalancerId(LoadBalancerId.newBuilder().setId(loadBalancerId).build())
                .build());
  }

  public Map<String, List<String>> getAllLoadBalancers() {
    Map<String, List<String>> results = new HashMap<>();
    String cursor = "";
    boolean hasMore = true;
    do {
      Page.Builder loadBalancerPage = Page.newBuilder().setPageSize(1000);
      if (!cursor.isEmpty()) {
        loadBalancerPage.setCursor(cursor);
      }
      GetAllLoadBalancersResult getAllLoadBalancersResult =
          loadBalancerServiceBlockingStub.getAllLoadBalancers(
              GetAllLoadBalancersRequest.newBuilder().setPage(loadBalancerPage).build());
      for (GetJobLoadBalancersResult result : getAllLoadBalancersResult.getJobLoadBalancersList()) {
        for (LoadBalancerId loadBalancerid : result.getLoadBalancersList()) {
          if (results.get(result.getJobId()) == null) {
            List<String> loadBalancers = new ArrayList<>();
            loadBalancers.add(loadBalancerid.getId());
            results.put(result.getJobId(), loadBalancers);
          } else {
            results.get(result.getJobId()).add(loadBalancerid.getId());
          }
        }
      }
      hasMore = getAllLoadBalancersResult.getPagination().getHasMore();
      cursor = getAllLoadBalancersResult.getPagination().getCursor();
    } while (hasMore);
    return results;
  }
}
