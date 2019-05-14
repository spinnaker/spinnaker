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

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.applicationautoscaling.AWSApplicationAutoScaling;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.Service;
import com.amazonaws.services.ecs.model.UpdateServiceRequest;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.ResizeServiceDescription;
import com.netflix.spinnaker.clouddriver.ecs.services.ContainerInformationService;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

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
    AmazonECS amazonECS = getAmazonEcsClient();

    String serviceName = description.getServerGroupName();
    Integer desiredCount = description.getCapacity().getDesired();
    String ecsClusterName =
        containerInformationService.getClusterName(
            serviceName, description.getAccount(), description.getRegion());

    UpdateServiceRequest updateServiceRequest =
        new UpdateServiceRequest()
            .withCluster(ecsClusterName)
            .withService(serviceName)
            .withDesiredCount(desiredCount);
    updateTaskStatus(String.format("Resizing %s to %s instances.", serviceName, desiredCount));
    Service service = amazonECS.updateService(updateServiceRequest).getService();
    updateTaskStatus(String.format("Done resizing %s to %s", serviceName, desiredCount));
    return service;
  }

  private void resizeAutoScalingGroup(Service service) {
    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();

    Integer desiredCount = description.getCapacity().getDesired();
    String ecsClusterName =
        containerInformationService.getClusterName(
            service.getServiceName(), description.getAccount(), description.getRegion());

    RegisterScalableTargetRequest request =
        new RegisterScalableTargetRequest()
            .withServiceNamespace(ServiceNamespace.Ecs)
            .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
            .withResourceId(
                String.format("service/%s/%s", ecsClusterName, service.getServiceName()))
            .withRoleARN(service.getRoleArn())
            .withMinCapacity(description.getCapacity().getMin())
            .withMaxCapacity(description.getCapacity().getMax());

    updateTaskStatus(
        String.format(
            "Resizing Scalable Target of %s to %s instances",
            service.getServiceName(), desiredCount));
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus(
        String.format(
            "Done resizing Scalable Target of %s to %s instances",
            service.getServiceName(), desiredCount));
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    String region = description.getRegion();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, region, false);
  }
}
