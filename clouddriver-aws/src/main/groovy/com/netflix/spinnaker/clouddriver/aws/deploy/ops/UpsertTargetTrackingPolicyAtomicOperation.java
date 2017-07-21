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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops;

import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyRequest;
import com.amazonaws.services.autoscaling.model.PutScalingPolicyResult;
import com.netflix.spinnaker.clouddriver.aws.deploy.description.UpsertTargetTrackingPolicyDescription;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.services.IdGenerator;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class UpsertTargetTrackingPolicyAtomicOperation implements AtomicOperation<UpsertTargetTrackingPolicyResult> {

  private UpsertTargetTrackingPolicyDescription description;

  public UpsertTargetTrackingPolicyAtomicOperation(UpsertTargetTrackingPolicyDescription description) {
    this.description = description;
  }

  @Autowired
  private AmazonClientProvider amazonClientProvider;

  private IdGenerator idGenerator = new IdGenerator();

  @Override
  public UpsertTargetTrackingPolicyResult operate(List priorOutputs) {
    String policyName = description.name;
    if (policyName == null) {
      policyName = description.serverGroupName + "-policy-" + idGenerator.nextId();
    }
    PutScalingPolicyRequest request = new PutScalingPolicyRequest()
      .withPolicyName(policyName)
      .withPolicyType("TargetTrackingScaling")
      .withAutoScalingGroupName(description.serverGroupName)
      .withEstimatedInstanceWarmup(description.estimatedInstanceWarmup)
      .withTargetTrackingConfiguration(description.targetTrackingConfiguration);

    final AmazonAutoScaling autoScaling = amazonClientProvider.getAutoScaling(description.getCredentials(), description.region, true);
    PutScalingPolicyResult scalingPolicyResult = autoScaling.putScalingPolicy(request);

    return new UpsertTargetTrackingPolicyResult(policyName, scalingPolicyResult.getPolicyARN());
  }
}
