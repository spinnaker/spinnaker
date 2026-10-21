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

import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeDeploymentSettings;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeUpdateServiceDescription;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;

/**
 * In-place {@code UpdateService} for the opt-in {@code ecs-native} provider.
 *
 * <p>This is the native deployment primitive: instead of creating a new versioned service, it rolls
 * an existing, durable ECS service to a new task definition and/or service shape and lets ECS
 * perform the deployment ({@code ROLLING} or {@code BLUE_GREEN}) with its circuit breaker and
 * optional automatic rollback. It also backs the native rollback stage, which sends only a task
 * definition. A {@code BLUE_GREEN} service keeps its ALB traffic-shift config: omitted load
 * balancers are left untouched, and supplied ones inherit the existing {@code
 * advancedConfiguration}.
 */
public class EcsNativeUpdateServiceAtomicOperation
    extends AbstractEcsAtomicOperation<EcsNativeUpdateServiceDescription, DeploymentResult>
    implements AtomicOperation<DeploymentResult> {

  public EcsNativeUpdateServiceAtomicOperation(EcsNativeUpdateServiceDescription description) {
    super(description, "UPDATE_ECS_SERVER_GROUP", true);
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    // Preserve the fail-fast behavior for an explicitly requested unsupported strategy. When the
    // strategy is omitted, the ownership lookup below is required to validate the stored strategy.
    EcsNativeDeploymentConfiguration.validateLifecycleSupport(description);
    updateTaskStatus("Initializing Update ECS Server Group (native) Operation...");

    EcsClient ecs = getAmazonEcsClient();

    String serviceName = description.getServerGroupName();
    String cluster = description.getEcsClusterName();
    if (StringUtils.isBlank(cluster)) {
      cluster =
          containerInformationService.getClusterName(
              serviceName, description.getAccount(), description.getRegion());
    }
    Service existingService = requireNativeServiceOwnership(cluster, serviceName);
    DeploymentConfiguration existingConfiguration = existingService.deploymentConfiguration();
    EcsNativeDeploymentConfiguration.validateLifecycleSupport(description, existingConfiguration);

    UpdateServiceRequest.Builder requestBuilder =
        UpdateServiceRequest.builder().cluster(cluster).service(serviceName);

    if (StringUtils.isNotBlank(description.getTaskDefinition())) {
      requestBuilder.taskDefinition(description.getTaskDefinition());
    }
    if (description.getDesiredCount() != null) {
      requestBuilder.desiredCount(description.getDesiredCount());
    }
    if (description.getNetworkConfiguration() != null) {
      requestBuilder.networkConfiguration(description.getNetworkConfiguration());
    }
    if (description.getServiceRegistries() != null) {
      requestBuilder.serviceRegistries(description.getServiceRegistries());
    }
    if (description.getPlacementConstraints() != null) {
      requestBuilder.placementConstraints(description.getPlacementConstraints());
    }
    if (description.getPlacementStrategy() != null) {
      requestBuilder.placementStrategy(description.getPlacementStrategy());
    }
    if (description.getCapacityProviderStrategy() != null) {
      requestBuilder.capacityProviderStrategy(description.getCapacityProviderStrategy());
    }
    if (StringUtils.isNotBlank(description.getPlatformVersion())) {
      requestBuilder.platformVersion(description.getPlatformVersion());
    }
    if (description.getHealthCheckGracePeriodSeconds() != null) {
      requestBuilder.healthCheckGracePeriodSeconds(description.getHealthCheckGracePeriodSeconds());
    }
    if (description.getEnableExecuteCommand() != null) {
      requestBuilder.enableExecuteCommand(description.getEnableExecuteCommand());
    }
    // Omitted load balancers leave the service's existing ones (including any blue/green
    // advancedConfiguration) untouched. Supplied ones replace them wholesale, so for a BLUE_GREEN
    // deployment -- requested explicitly or inherited from the service -- carry the existing
    // traffic-shift config over rather than silently dropping it.
    if (description.getLoadBalancers() != null) {
      List<LoadBalancer> loadBalancers = description.getLoadBalancers();
      if (EcsNativeDeploymentSettings.isBlueGreen(
          EcsNativeDeploymentConfiguration.effectiveStrategy(description, existingConfiguration))) {
        loadBalancers =
            EcsNativeBlueGreenConfiguration.preserveAdvancedConfiguration(
                loadBalancers, existingService.loadBalancers());
      }
      requestBuilder.loadBalancers(loadBalancers);
    }

    DeploymentConfiguration deploymentConfiguration =
        EcsNativeDeploymentConfiguration.forUpdate(description, existingConfiguration);
    if (deploymentConfiguration != null) {
      requestBuilder.deploymentConfiguration(deploymentConfiguration);
    }

    requestBuilder.forceNewDeployment(description.isForceNewDeployment());

    updateTaskStatus(String.format("Updating ECS service %s in cluster %s.", serviceName, cluster));
    Service service = ecs.updateService(requestBuilder.build()).service();
    updateTaskStatus(String.format("Done updating ECS service %s.", serviceName));

    return buildDeploymentResult(service, cluster);
  }

  private DeploymentResult buildDeploymentResult(Service service, String cluster) {
    String resolvedServiceName =
        StringUtils.defaultIfBlank(service.serviceName(), description.getServerGroupName());
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(description.getRegion(), resolvedServiceName);

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(
        Collections.singletonList(description.getRegion() + ":" + resolvedServiceName));
    result.setServerGroupNameByRegion(namesByRegion);

    if (StringUtils.isNotBlank(service.taskDefinition())) {
      Deployment deployment = new Deployment();
      deployment.setCloudProvider(EcsNativeCloudProvider.ID);
      deployment.setAccount(description.getAccount());
      deployment.setLocation(description.getRegion());
      deployment.setServerGroupName(resolvedServiceName);
      deployment.getMetadata().put("ecsNativeExpectedTaskDefinition", service.taskDefinition());
      String serviceDeploymentArn = resolveCurrentServiceDeployment(service, cluster);
      if (StringUtils.isNotBlank(serviceDeploymentArn)) {
        deployment.getMetadata().put("ecsNativeExpectedServiceDeploymentArn", serviceDeploymentArn);
      }
      result.setDeployments(Collections.singleton(deployment));
    }
    return result;
  }
}
