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

import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult.Deployment;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.EcsNativeServiceTag;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.EcsNativeCreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsServerGroupName;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.AdvancedConfiguration;
import software.amazon.awssdk.services.ecs.model.CreateServiceRequest;
import software.amazon.awssdk.services.ecs.model.DeploymentConfiguration;
import software.amazon.awssdk.services.ecs.model.DescribeServicesRequest;
import software.amazon.awssdk.services.ecs.model.DescribeServicesResponse;
import software.amazon.awssdk.services.ecs.model.LoadBalancer;
import software.amazon.awssdk.services.ecs.model.MonitoringConfiguration;
import software.amazon.awssdk.services.ecs.model.Service;
import software.amazon.awssdk.services.ecs.model.Tag;
import software.amazon.awssdk.services.ecs.model.TaskDefinition;
import software.amazon.awssdk.services.ecs.model.UpdateServiceRequest;

/**
 * Create-server-group operation for the opt-in {@code ecs-native} provider.
 *
 * <p>It reuses all of {@link CreateServerGroupAtomicOperation}'s task-definition, load-balancer,
 * networking, scaling and tagging logic, and diverges in two ways:
 *
 * <ol>
 *   <li>The native ECS {@link DeploymentConfiguration} (rolling bounds, circuit-breaker rollback,
 *       deployment alarms, and the {@code ROLLING}/{@code BLUE_GREEN} strategy with its optional
 *       ALB traffic-shift config) is configurable rather than hard-coded (see {@link
 *       #makeServiceRequest}).
 *   <li>ecs-native always deploys to a single durable service in place: {@link #operate} computes
 *       the fixed service name and, if that service already exists, rolls it via a native {@code
 *       UpdateService} instead of creating a new versioned service. Only the very first deploy
 *       (service does not exist yet) falls through to {@code CreateService}.
 *   <li>The service uses a fixed, unversioned name (the cluster/family name, e.g. {@code
 *       app-stack-detail}) with no {@code -vNNN} suffix, since ecs-native keeps a single durable
 *       service and rolls new revisions in place (see {@link #buildEcsServerGroupName}).
 * </ol>
 *
 * <p>The shared {@link CreateServerGroupAtomicOperation} is not modified; this operation is a
 * subclass so the original {@code ecs} provider is unaffected.
 */
public class EcsNativeCreateServerGroupAtomicOperation extends CreateServerGroupAtomicOperation {

  public EcsNativeCreateServerGroupAtomicOperation(
      EcsNativeCreateServerGroupDescription description) {
    super(description);
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    // Preserve the fail-fast behavior for an explicitly requested unsupported strategy. When the
    // strategy is omitted, the service lookup below is required to validate the stored strategy.
    EcsNativeDeploymentConfiguration.validateLifecycleSupport(nativeDescription());

    // ecs-native is always in-place: there is one durable ECS service per cluster, named by the
    // fixed (unversioned) family name. If that service already exists, roll it via a native
    // UpdateService; only the first-ever deploy (service absent) falls through to CreateService.
    Service existingService = resolveExistingService();
    if (existingService != null) {
      EcsNativeDeploymentConfiguration.validateLifecycleSupport(
          nativeDescription(), existingService.deploymentConfiguration());
      return updateExistingServiceInPlace(existingService);
    }
    updateTaskStatus("No existing ecs-native service found; creating the initial durable service.");
    return super.operate(priorOutputs);
  }

  /**
   * ecs-native uses a fixed, unversioned service name (the cluster/family name, e.g. {@code
   * app-stack-detail}) rather than the resolver's next {@code -vNNN} slot. There is one durable
   * service per cluster that is rolled in place, so a per-deploy version sequence has no meaning;
   * the moniker sequence is left null (revision visibility comes from the deployed image /
   * task-definition detail, not the name). Overriding here keeps the shared {@link
   * CreateServerGroupAtomicOperation} — and the classic {@code ecs} provider's versioned naming —
   * untouched.
   */
  @Override
  protected EcsServerGroupName buildEcsServerGroupName(EcsClient ecs, Namer<EcsResource> namer) {
    Moniker moniker = description.getMoniker();
    if (moniker == null) {
      moniker =
          Moniker.builder()
              .app(description.getApplication())
              .stack(description.getStack())
              .detail(description.getFreeFormDetails())
              .build();
    } else {
      // Defensively drop any sequence the caller supplied: a fixed-name service has no version.
      moniker =
          Moniker.builder()
              .app(moniker.getApp())
              .cluster(moniker.getCluster())
              .stack(moniker.getStack())
              .detail(moniker.getDetail())
              .build();
    }
    return new EcsServerGroupName(moniker, true);
  }

  /**
   * Returns the name of the existing durable ecs-native service to roll in place, or {@code null}
   * if it does not exist yet (first deploy). ecs-native uses a fixed, unversioned service name, so
   * we compute that name and ask ECS whether such a service is currently ACTIVE/DRAINING in the
   * cluster -- independent of any deploy-stage source block. This is what makes every ecs-native
   * redeploy an in-place UpdateService rather than a colliding CreateService.
   */
  protected Service resolveExistingService() {
    EcsClient ecs = getAmazonEcsClient();
    String fixedServiceName = buildEcsServerGroupName(ecs, null).getServiceName();

    DescribeServicesRequest request =
        DescribeServicesRequest.builder()
            .cluster(description.getEcsClusterName())
            .services(fixedServiceName)
            .includeWithStrings("TAGS")
            .build();
    DescribeServicesResponse result = ecs.describeServices(request);

    // A service that has been deleted lingers as INACTIVE; treat only ACTIVE/DRAINING as existing.
    if (result.services().isEmpty() || "INACTIVE".equals(result.services().get(0).status())) {
      return null;
    }
    Service service = result.services().get(0);
    if (!EcsNativeServiceTag.isNative(service.tags())) {
      throw new IllegalStateException(
          "ECS service "
              + fixedServiceName
              + " already exists but is not marked as owned by ecs-native; refusing to update an external service.");
    }
    return service;
  }

  /** Retained for tests and callers that only need the fixed service name. */
  protected String resolveExistingServiceName() {
    Service service = resolveExistingService();
    return service == null ? null : service.serviceName();
  }

  private DeploymentResult updateExistingServiceInPlace(Service existingService) {
    String existingServiceName = existingService.serviceName();
    updateTaskStatus(
        "Rolling ecs-native service "
            + existingServiceName
            + " in place via native UpdateService...");

    EcsClient ecs = getAmazonEcsClient();
    String taskRoleArn = resolveTaskRoleArn(getCredentials());

    // Register a new revision under the same family as the existing service.
    EcsServerGroupName serverGroupName = new EcsServerGroupName(existingServiceName);
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, taskRoleArn, serverGroupName);

    UpdateServiceRequest.Builder requestBuilder =
        buildAuthoritativeUpdateRequest(
            ecs, taskDefinition.taskDefinitionArn(), serverGroupName, existingService);

    EcsNativeBlueGreenConfiguration.validateInPlaceConfig(nativeDescription());

    DeploymentConfiguration deploymentConfiguration =
        EcsNativeDeploymentConfiguration.forUpdate(
            nativeDescription(), existingService.deploymentConfiguration());
    if (deploymentConfiguration != null) {
      requestBuilder.deploymentConfiguration(deploymentConfiguration);
    }

    // For a blue/green ALB traffic shift ECS needs the advancedConfiguration on the service's
    // load balancer, and UpdateService only carries it when we also (re)send loadBalancers.
    AdvancedConfiguration advancedConfiguration =
        EcsNativeBlueGreenConfiguration.buildAdvancedConfiguration(nativeDescription());
    if (advancedConfiguration != null) {
      Collection<LoadBalancer> loadBalancers =
          retrieveLoadBalancers(serverGroupName.getContainerName());
      requestBuilder.loadBalancers(
          EcsNativeBlueGreenConfiguration.withAdvancedConfiguration(
              new ArrayList<>(loadBalancers), advancedConfiguration));
    }

    Service service = ecs.updateService(requestBuilder.build()).service();
    if (description.getCapacity() != null && !keepExistingCapacity()) {
      registerAutoScalingGroup(getCredentials(), service, null);
    }
    updateTaskStatus("Done rolling ecs-native service " + existingServiceName + " in place.");

    return buildDeploymentResult(service);
  }

  private boolean hasLoadBalancerConfiguration() {
    return description.getTargetGroup() != null
        || description.getTargetGroupMappings() != null
        || description.getLoadBalancedContainer() != null;
  }

  /**
   * Builds an authoritative UpdateService request from the same service-shape contract used by the
   * initial CreateService request. ECS does not support launchType changes through UpdateService,
   * so launchType remains the deliberate exception; capacity provider strategy is the mutable ECS
   * replacement exposed here.
   *
   * <p>"Copy existing capacity" ({@link #keepExistingCapacity()}) only decides capacity, as in
   * classic ecs: the desired count is not sent and the scalable target is not re-registered.
   * Service settings (network, load balancers, placement, ...) always come from the stage, falling
   * back to the live service per field when the stage leaves one unset.
   */
  private UpdateServiceRequest.Builder buildAuthoritativeUpdateRequest(
      EcsClient ecs,
      String taskDefinitionArn,
      EcsServerGroupName serverGroupName,
      Service existingService) {
    if (description.getCapacity() == null) {
      // A request without any capacity carries no service shape either (Deck always sends a
      // capacity), so only the task definition changes.
      return UpdateServiceRequest.builder()
          .cluster(description.getEcsClusterName())
          .service(existingService.serviceName())
          .taskDefinition(taskDefinitionArn)
          .networkConfiguration(existingService.networkConfiguration())
          .serviceRegistries(existingService.serviceRegistries())
          .placementConstraints(existingService.placementConstraints())
          .placementStrategy(existingService.placementStrategy())
          .capacityProviderStrategy(existingService.capacityProviderStrategy())
          .platformVersion(existingService.platformVersion())
          .healthCheckGracePeriodSeconds(existingService.healthCheckGracePeriodSeconds())
          .enableExecuteCommand(
              description.getEnableExecuteCommand() != null
                  ? description.getEnableExecuteCommand()
                  : existingService.enableExecuteCommand())
          .loadBalancers(existingService.loadBalancers())
          .forceNewDeployment(true);
    }

    Namer<EcsResource> namer =
        NamerRegistry.lookup()
            .withProvider(EcsCloudProvider.ID)
            .withAccount(description.getAccount())
            .withResource(EcsResource.class);
    boolean stageOwnsCapacity = !keepExistingCapacity();
    // makeServiceRequest needs a count to build the shared service shape; it is only sent below
    // when
    // the stage owns capacity.
    Integer desiredCount =
        stageOwnsCapacity ? description.getCapacity().getDesired() : existingService.desiredCount();
    CreateServiceRequest serviceRequest =
        makeServiceRequest(taskDefinitionArn, serverGroupName, desiredCount, namer, true);

    UpdateServiceRequest.Builder requestBuilder =
        UpdateServiceRequest.builder()
            .cluster(serviceRequest.cluster())
            .service(serviceRequest.serviceName())
            .taskDefinition(taskDefinitionArn)
            .networkConfiguration(
                serviceRequest.networkConfiguration() != null
                    ? serviceRequest.networkConfiguration()
                    : existingService.networkConfiguration())
            .serviceRegistries(
                description.getServiceDiscoveryAssociations() != null
                    ? serviceRequest.serviceRegistries()
                    : existingService.serviceRegistries())
            .placementConstraints(
                description.getPlacementConstraints() != null
                    ? description.getPlacementConstraints()
                    : existingService.placementConstraints())
            .placementStrategy(
                description.getPlacementStrategySequence() != null
                    ? description.getPlacementStrategySequence()
                    : existingService.placementStrategy())
            .capacityProviderStrategy(
                description.getCapacityProviderStrategy() != null
                    ? description.getCapacityProviderStrategy()
                    : existingService.capacityProviderStrategy())
            .platformVersion(
                StringUtils.isNotBlank(description.getPlatformVersion())
                    ? description.getPlatformVersion()
                    : existingService.platformVersion())
            .healthCheckGracePeriodSeconds(
                description.getHealthCheckGracePeriodSeconds() != null
                    ? description.getHealthCheckGracePeriodSeconds()
                    : existingService.healthCheckGracePeriodSeconds())
            .enableExecuteCommand(
                description.getEnableExecuteCommand() != null
                    ? description.getEnableExecuteCommand()
                    : existingService.enableExecuteCommand())
            .loadBalancers(
                hasLoadBalancerConfiguration()
                    ? serviceRequest.loadBalancers()
                    : existingService.loadBalancers())
            .forceNewDeployment(true);

    if (stageOwnsCapacity) {
      requestBuilder.desiredCount(desiredCount);
    }
    return requestBuilder;
  }

  /**
   * Overrides the full variant: the classic create path calls it directly (with the resolved
   * monitoring configuration), and the shorter overload delegates to it, so both the first create
   * and the in-place update get the native deployment configuration and the ownership tag.
   */
  @Override
  protected CreateServiceRequest makeServiceRequest(
      String taskDefinitionArn,
      EcsServerGroupName newServerGroupName,
      Integer desiredCount,
      Namer<EcsResource> namer,
      boolean taggingEnabled,
      MonitoringConfiguration monitoringConfiguration) {

    CreateServiceRequest request =
        super.makeServiceRequest(
            taskDefinitionArn,
            newServerGroupName,
            desiredCount,
            namer,
            taggingEnabled,
            monitoringConfiguration);

    DeploymentConfiguration deploymentConfiguration =
        EcsNativeDeploymentConfiguration.forCreate(
            nativeDescription(), request.deploymentConfiguration());

    CreateServiceRequest.Builder requestBuilder =
        request.toBuilder().deploymentConfiguration(deploymentConfiguration);

    EcsNativeBlueGreenConfiguration.validateLoadBalancerConfig(
        nativeDescription(), request.loadBalancers());

    AdvancedConfiguration advancedConfiguration =
        EcsNativeBlueGreenConfiguration.buildAdvancedConfiguration(nativeDescription());
    if (advancedConfiguration != null) {
      requestBuilder.loadBalancers(
          EcsNativeBlueGreenConfiguration.withAdvancedConfiguration(
              request.loadBalancers(), advancedConfiguration));
    }

    if (!taggingEnabled) {
      throw new IllegalArgumentException(
          "ecs-native requires ECS service tagging, but serviceLongArnFormat and taskLongArnFormat "
              + "are not both enabled for account "
              + description.getAccount()
              + ". Enable both account settings before creating an ecs-native service.");
    }

    CreateServiceRequest serviceRequest = requestBuilder.build();
    List<Tag> serviceTags = new ArrayList<>(serviceRequest.tags());
    serviceTags.removeIf(tag -> EcsNativeServiceTag.KEY.equals(tag.key()));
    serviceTags.add(EcsNativeServiceTag.tag());
    return serviceRequest.toBuilder().tags(serviceTags).build();
  }

  /**
   * Resolves the task role ARN from the account credentials. Mirrors the (private) inference in the
   * shared create operation; exposed as {@code protected} so it can be overridden in tests.
   */
  protected String resolveTaskRoleArn(AmazonCredentials credentials) {
    return inferAssumedRoleArn(credentials);
  }

  /**
   * The base {@code CreateServerGroupDescription} overrides {@code getRegion()} to derive it from
   * {@code getAvailabilityZones()} unconditionally -- there's no way to reach the plain {@code
   * region} field on {@code AbstractECSDescription} once that override is in the hierarchy. That's
   * fine for a normal deploy (availability zones are set), but an in-place redeploy triggered
   * without an availability-zone map would make the base override throw a {@code
   * NullPointerException}.
   *
   * <p>This override is defensive and flag-independent: prefer the availability-zone-derived region
   * (the normal case), and only fall back to the deploy-stage source region when the AZ map is
   * absent -- which is exactly the redeploy-with-source case that would otherwise NPE.
   */
  @Override
  protected String getRegion() {
    boolean hasAvailabilityZones =
        description.getAvailabilityZones() != null && !description.getAvailabilityZones().isEmpty();
    if (!hasAvailabilityZones
        && description.getSource() != null
        && StringUtils.isNotBlank(description.getSource().getRegion())) {
      return description.getSource().getRegion();
    }
    return super.getRegion();
  }

  @Override
  protected DeploymentResult makeDeploymentResult(Service service) {
    return buildDeploymentResult(service);
  }

  private DeploymentResult buildDeploymentResult(Service service) {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), service.serviceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(
        Collections.singletonList(getRegion() + ":" + service.serviceName()));
    result.setServerGroupNameByRegion(namesByRegion);

    if (StringUtils.isNotBlank(service.taskDefinition())) {
      Deployment deployment = new Deployment();
      deployment.setCloudProvider(EcsNativeCloudProvider.ID);
      deployment.setAccount(description.getAccount());
      deployment.setLocation(getRegion());
      deployment.setServerGroupName(service.serviceName());
      deployment.getMetadata().put("ecsNativeExpectedTaskDefinition", service.taskDefinition());
      String serviceDeploymentArn =
          resolveCurrentServiceDeployment(service, description.getEcsClusterName());
      if (StringUtils.isNotBlank(serviceDeploymentArn)) {
        deployment.getMetadata().put("ecsNativeExpectedServiceDeploymentArn", serviceDeploymentArn);
      }
      result.setDeployments(Collections.singleton(deployment));
    }
    return result;
  }

  /**
   * Classic ecs honors "copy existing capacity" by reading the previous service's desired count and
   * scalable target. ecs-native has no previous service: the durable service is its own source, so
   * honoring the flag means not touching its desired count or scalable target (which may be owned
   * by autoscaling or Terraform).
   */
  private boolean keepExistingCapacity() {
    EcsNativeCreateServerGroupDescription settings = nativeDescription();
    return Boolean.TRUE.equals(settings.getUseSourceCapacity())
        || (settings.getSource() != null
            && Boolean.TRUE.equals(settings.getSource().getUseSourceCapacity()));
  }

  private EcsNativeCreateServerGroupDescription nativeDescription() {
    return (EcsNativeCreateServerGroupDescription) description;
  }
}
