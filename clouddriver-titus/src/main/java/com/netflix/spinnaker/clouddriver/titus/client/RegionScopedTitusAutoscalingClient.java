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
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.clouddriver.titus.client.model.GrpcChannelFactory;
import com.netflix.titus.grpc.protogen.*;
import java.util.List;

public class RegionScopedTitusAutoscalingClient implements TitusAutoscalingClient {

  /** Default connect timeout in milliseconds */
  private static final long DEFAULT_CONNECT_TIMEOUT = 60000;

  private final AutoScalingServiceGrpc.AutoScalingServiceBlockingStub
      autoScalingServiceBlockingStub;

  public RegionScopedTitusAutoscalingClient(
      TitusRegion titusRegion,
      Registry registry,
      String environment,
      String eurekaName,
      GrpcChannelFactory channelFactory) {
    this.autoScalingServiceBlockingStub =
        AutoScalingServiceGrpc.newBlockingStub(
            channelFactory.build(
                titusRegion, environment, eurekaName, DEFAULT_CONNECT_TIMEOUT, registry));
  }

  @Override
  public List<ScalingPolicyResult> getAllScalingPolicies() {
    return autoScalingServiceBlockingStub
        .getAllScalingPolicies(Empty.newBuilder().build())
        .getItemsList();
  }

  @Override
  public List<ScalingPolicyResult> getJobScalingPolicies(String jobId) {
    JobId request = JobId.newBuilder().setId(jobId).build();
    return autoScalingServiceBlockingStub.getJobScalingPolicies(request).getItemsList();
  }

  @Override
  public ScalingPolicyResult getScalingPolicy(String policyId) {
    return autoScalingServiceBlockingStub
        .getScalingPolicy(ScalingPolicyID.newBuilder().setId(policyId).build())
        .getItems(0);
  }

  @Override
  public ScalingPolicyID createScalingPolicy(PutPolicyRequest policy) {
    return TitusClientAuthenticationUtil.attachCaller(autoScalingServiceBlockingStub)
        .setAutoScalingPolicy(policy);
  }

  @Override
  public void updateScalingPolicy(UpdatePolicyRequest policy) {
    TitusClientAuthenticationUtil.attachCaller(autoScalingServiceBlockingStub)
        .updateAutoScalingPolicy(policy);
  }

  @Override
  public void deleteScalingPolicy(DeletePolicyRequest request) {
    TitusClientAuthenticationUtil.attachCaller(autoScalingServiceBlockingStub)
        .deleteAutoScalingPolicy(request);
  }
}
