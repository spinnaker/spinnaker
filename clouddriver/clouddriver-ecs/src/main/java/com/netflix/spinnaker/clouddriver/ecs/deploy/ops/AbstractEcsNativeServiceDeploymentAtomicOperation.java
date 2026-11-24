/*
 * Copyright 2026 Spinnaker contributors.
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

import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeServiceDeploymentDescription;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.model.DescribeServiceDeploymentsRequest;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.ServiceDeployment;

/** Common fail-closed resolution for explicit native ECS service-deployment transitions. */
public abstract class AbstractEcsNativeServiceDeploymentAtomicOperation
    extends AbstractEcsAtomicOperation<EcsNativeServiceDeploymentDescription, Void> {

  AbstractEcsNativeServiceDeploymentAtomicOperation(
      EcsNativeServiceDeploymentDescription description, String phase) {
    super(description, phase, true);
  }

  protected Service requireOwnedService() {
    String serviceName = description.getServerGroupName();
    if (StringUtils.isBlank(serviceName)) {
      throw new IllegalArgumentException(
          "ecs-native service deployment operation requires serverGroupName");
    }
    if (StringUtils.isBlank(description.getEcsNativeExpectedServiceDeploymentArn())) {
      throw new IllegalArgumentException(
          "ecs-native service deployment operation requires ecsNativeExpectedServiceDeploymentArn");
    }
    String cluster = description.getEcsClusterName();
    if (StringUtils.isBlank(cluster)) {
      cluster = getCluster(serviceName, description.getAccount());
    }
    if (StringUtils.isBlank(cluster)) {
      throw new IllegalArgumentException(
          "ecs-native service deployment operation requires ecsClusterName");
    }
    return requireNativeServiceOwnership(cluster, serviceName);
  }

  protected String expectedServiceDeploymentArn() {
    return description.getEcsNativeExpectedServiceDeploymentArn();
  }

  /**
   * Describes the pinned service deployment and fails closed unless it belongs to the owned
   * service. The tag check on {@code serverGroupName} alone does not bind the caller-supplied ARN
   * to that service, so without this an owned service name plus another service's deployment ARN
   * would act on a service ecs-native does not own.
   */
  protected ServiceDeployment requireOwnedServiceDeployment(Service ownedService) {
    String serviceDeploymentArn = expectedServiceDeploymentArn();
    ServiceDeployment deployment =
        getAmazonEcsClient()
            .describeServiceDeployments(
                DescribeServiceDeploymentsRequest.builder()
                    .serviceDeploymentArns(serviceDeploymentArn)
                    .build())
            .serviceDeployments()
            .stream()
            .filter(candidate -> serviceDeploymentArn.equals(candidate.serviceDeploymentArn()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Service deployment " + serviceDeploymentArn + " was not found"));

    boolean sameService =
        StringUtils.isNotBlank(ownedService.serviceArn())
            && ownedService.serviceArn().equals(deployment.serviceArn());
    boolean sameCluster =
        StringUtils.isBlank(ownedService.clusterArn())
            || ownedService.clusterArn().equals(deployment.clusterArn());
    if (!sameService || !sameCluster) {
      throw new IllegalStateException(
          "Service deployment "
              + serviceDeploymentArn
              + " does not belong to ecs-native service "
              + ownedService.serviceName()
              + "; refusing to act on it.");
    }
    return deployment;
  }

  @Override
  public Void operate(List priorOutputs) {
    Service ownedService = requireOwnedService();
    transition(requireOwnedServiceDeployment(ownedService));
    return null;
  }

  /** Acts on a deployment already verified to belong to the owned native service. */
  protected abstract void transition(ServiceDeployment deployment);
}
