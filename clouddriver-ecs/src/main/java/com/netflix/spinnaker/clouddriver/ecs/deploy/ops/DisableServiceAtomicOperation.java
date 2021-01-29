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

import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.*;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ModifyServiceDescription;
import java.util.List;

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
    AmazonECS ecs = getAmazonEcsClient();
    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();

    String service = description.getServerGroupName();
    String account = description.getAccount();
    String cluster = getCluster(service, account);

    DescribeScalableTargetsRequest describeRequest =
        new DescribeScalableTargetsRequest()
            .withServiceNamespace(ServiceNamespace.Ecs)
            .withResourceIds(String.format("service/%s/%s", cluster, service))
            .withScalableDimension(ScalableDimension.EcsServiceDesiredCount);
    DescribeScalableTargetsResult describeResult =
        autoScalingClient.describeScalableTargets(describeRequest);

    if (isSuspended(describeResult)) {
      updateTaskStatus(
          String.format(
              "Autoscaling already suspended on server group %s for %s.", service, account));
    } else {
      updateTaskStatus(
          String.format("Suspending autoscaling on %s server group for %s.", service, account));
      RegisterScalableTargetRequest suspendRequest =
          new RegisterScalableTargetRequest()
              .withServiceNamespace(ServiceNamespace.Ecs)
              .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
              .withResourceId(String.format("service/%s/%s", cluster, service))
              .withSuspendedState(
                  new SuspendedState()
                      .withDynamicScalingInSuspended(true)
                      .withDynamicScalingOutSuspended(true)
                      .withScheduledScalingSuspended(true));
      autoScalingClient.registerScalableTarget(suspendRequest);
      updateTaskStatus(
          String.format("Autoscaling on server group %s suspended for %s.", service, account));
    }

    updateTaskStatus(String.format("Disabling %s server group for %s.", service, account));
    UpdateServiceRequest request =
        new UpdateServiceRequest().withCluster(cluster).withService(service).withDesiredCount(0);
    ecs.updateService(request);
    updateTaskStatus(String.format("Server group %s disabled for %s.", service, account));
  }

  private boolean isSuspended(DescribeScalableTargetsResult describeResult) {
    if (describeResult != null
        && describeResult.getScalableTargets() != null
        && describeResult.getScalableTargets().size() > 0) {
      ScalableTarget target =
          describeResult.getScalableTargets().stream()
              .filter(
                  e ->
                      (e.getScalableDimension()
                          .equals(ScalableDimension.EcsServiceDesiredCount.toString())))
              .findFirst()
              .orElse(null);

      return (target != null)
          && target.getSuspendedState().getScheduledScalingSuspended()
          && target.getSuspendedState().getDynamicScalingInSuspended()
          && target.getSuspendedState().getDynamicScalingOutSuspended();
    }

    return false;
  }
}
