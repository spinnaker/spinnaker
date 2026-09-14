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

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;
import software.amazon.awssdk.services.ecs.model.UpdateServiceResponse;

public class ResizeServiceAtomicOperation
    extends AbstractEcsAtomicOperation<ResizeServiceDescription, Void>
    implements AtomicOperation<Void> {
  @Autowired ContainerInformationService containerInformationService;

  public ResizeServiceAtomicOperation(ResizeServiceDescription description) {
    super(description, "RESIZE_ECS_SERVER_GROUP");
  }

  @Override
  public Void operate(List priorOutputs) {
    updateTaskStatus("Initializing Resize ECS Server Group Operation...");

    Service service = resizeService();
    resizeAutoScalingGroup(service);

    return null;
  }

  private Service resizeService() {
    EcsClient amazonECS = getAmazonEcsClient();

    String serviceName = description.getServerGroupName();
    Integer desiredCount = description.getCapacity().getDesired();
    String ecsClusterName =
        containerInformationService.getClusterName(
            serviceName, description.getAccount(), description.getRegion());

    UpdateServiceRequest updateServiceRequest =
        UpdateServiceRequest.builder()
            .cluster(ecsClusterName)
            .service(serviceName)
            .desiredCount(desiredCount)
            .build();
    updateTaskStatus(String.format("Resizing %s to %s instances.", serviceName, desiredCount));
    UpdateServiceResponse response = amazonECS.updateService(updateServiceRequest);
    Service service = response.service();
    updateTaskStatus(String.format("Done resizing %s to %s", serviceName, desiredCount));
    return service;
  }

  private void resizeAutoScalingGroup(Service service) {
    ApplicationAutoScalingClient autoScalingClient = getAmazonApplicationAutoScalingClient();

    Integer desiredCount = description.getCapacity().getDesired();
    String ecsClusterName =
        containerInformationService.getClusterName(
            service.serviceName(), description.getAccount(), description.getRegion());

    RegisterScalableTargetRequest request =
        RegisterScalableTargetRequest.builder()
            .serviceNamespace(ServiceNamespace.ECS)
            .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
            .resourceId(String.format("service/%s/%s", ecsClusterName, service.serviceName()))
            .minCapacity(description.getCapacity().getMin())
            .maxCapacity(description.getCapacity().getMax())
            .build();

    updateTaskStatus(
        String.format(
            "Resizing Scalable Target of %s to %s instances", service.serviceName(), desiredCount));
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus(
        String.format(
            "Done resizing Scalable Target of %s to %s instances",
            service.serviceName(), desiredCount));
  }
}
