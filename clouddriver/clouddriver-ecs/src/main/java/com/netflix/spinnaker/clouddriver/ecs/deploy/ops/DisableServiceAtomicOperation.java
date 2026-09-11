/*
 * Copyright 2018 Lookout, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.deploy.ops;

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import java.util.List;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.applicationautoscaling.model.SuspendedState;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;

public class DisableServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ModifyServiceDescription, Void> {

  public DisableServiceAtomicOperation(ModifyServiceDescription description) {
    super(description, "DISABLE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Disable Amazon ECS Server Group Operation...");
    disableService();
    return null;
  }

  private void disableService() {
    EcsClient ecs = getAmazonEcsClient();
    ApplicationAutoScalingClient autoScalingClient = getAmazonApplicationAutoScalingClient();

    String service = description.getServerGroupName();
    String account = description.getAccount();
    String cluster = getCluster(service, account);

    DescribeScalableTargetsRequest describeRequest =
        DescribeScalableTargetsRequest.builder()
            .serviceNamespace(ServiceNamespace.ECS)
            .resourceIds(String.format("service/%s/%s", cluster, service))
            .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
            .build();
    DescribeScalableTargetsResponse describeResult =
        autoScalingClient.describeScalableTargets(describeRequest);

    if (isSuspended(describeResult)) {
      updateTaskStatus(
          String.format(
              "Autoscaling already suspended on server group %s for %s.", service, account));
    } else {
      updateTaskStatus(
          String.format("Suspending autoscaling on %s server group for %s.", service, account));
      RegisterScalableTargetRequest suspendRequest =
          RegisterScalableTargetRequest.builder()
              .serviceNamespace(ServiceNamespace.ECS)
              .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
              .resourceId(String.format("service/%s/%s", cluster, service))
              .suspendedState(
                  SuspendedState.builder()
                      .dynamicScalingInSuspended(true)
                      .dynamicScalingOutSuspended(true)
                      .scheduledScalingSuspended(true)
                      .build())
              .build();
      autoScalingClient.registerScalableTarget(suspendRequest);
      updateTaskStatus(
          String.format("Autoscaling on server group %s suspended for %s.", service, account));
    }

    updateTaskStatus(String.format("Disabling %s server group for %s.", service, account));
    UpdateServiceRequest request =
        UpdateServiceRequest.builder().cluster(cluster).service(service).desiredCount(0).build();
    ecs.updateService(request);
    updateTaskStatus(String.format("Server group %s disabled for %s.", service, account));
  }

  private boolean isSuspended(DescribeScalableTargetsResponse describeResult) {
    if (describeResult != null
        && describeResult.scalableTargets() != null
        && describeResult.scalableTargets().size() > 0) {
      ScalableTarget target =
          describeResult.scalableTargets().stream()
              .filter(
                  e -> (e.scalableDimension().equals(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)))
              .findFirst()
              .orElse(null);

      return (target != null)
          && target.suspendedState().scheduledScalingSuspended()
          && target.suspendedState().dynamicScalingInSuspended()
          && target.suspendedState().dynamicScalingOutSuspended();
    }

    return false;
  }
}
