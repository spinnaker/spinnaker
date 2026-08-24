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
import java.util.ArrayList;
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

public class EnableServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ModifyServiceDescription, Void> {

  public EnableServiceAtomicOperation(ModifyServiceDescription description) {
    super(description, "ENABLE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Enable Amazon ECS Server Group Operation...");
    enableService();
    return null;
  }

  private void enableService() {
    EcsClient ecsClient = getAmazonEcsClient();
    ApplicationAutoScalingClient autoScalingClient = getAmazonApplicationAutoScalingClient();

    String service = description.getServerGroupName();
    String account = description.getAccount();
    String cluster = getCluster(service, account);

    UpdateServiceRequest request =
        UpdateServiceRequest.builder()
            .cluster(cluster)
            .service(service)
            .desiredCount(getMaxCapacity(cluster))
            .build();

    updateTaskStatus(String.format("Enabling %s server group for %s.", service, account));
    ecsClient.updateService(request);
    updateTaskStatus(String.format("Server group %s enabled for %s.", service, account));

    updateTaskStatus(
        String.format("Resuming autoscaling on %s server group for %s.", service, account));
    RegisterScalableTargetRequest resumeRequest =
        RegisterScalableTargetRequest.builder()
            .serviceNamespace(ServiceNamespace.ECS)
            .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
            .resourceId(String.format("service/%s/%s", cluster, service))
            .suspendedState(
                SuspendedState.builder()
                    .dynamicScalingInSuspended(false)
                    .dynamicScalingOutSuspended(false)
                    .scheduledScalingSuspended(false)
                    .build())
            .build();
    autoScalingClient.registerScalableTarget(resumeRequest);
    updateTaskStatus(
        String.format("Autoscaling on server group %s resumed for %s.", service, account));
  }

  private Integer getMaxCapacity(String cluster) {
    ScalableTarget target = getScalableTarget(cluster);
    if (target != null) {
      return target.maxCapacity();
    }
    return 1;
  }

  private ScalableTarget getScalableTarget(String cluster) {
    ApplicationAutoScalingClient appASClient = getAmazonApplicationAutoScalingClient();

    List<String> resourceIds = new ArrayList<>();
    resourceIds.add(String.format("service/%s/%s", cluster, description.getServerGroupName()));

    DescribeScalableTargetsRequest request =
        DescribeScalableTargetsRequest.builder()
            .resourceIds(resourceIds)
            .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
            .serviceNamespace(ServiceNamespace.ECS)
            .build();

    DescribeScalableTargetsResponse result = appASClient.describeScalableTargets(request);

    if (result.scalableTargets().isEmpty()) {
      return null;
    }

    if (result.scalableTargets().size() == 1) {
      return result.scalableTargets().get(0);
    }

    throw new Error("Multiple Scalable Targets found");
  }
}
