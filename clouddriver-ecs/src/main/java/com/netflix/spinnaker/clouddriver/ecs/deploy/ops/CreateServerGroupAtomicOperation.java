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
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import com.amazonaws.services.applicationautoscaling.model.DescribeScalableTargetsResult;
import com.amazonaws.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import com.amazonaws.services.applicationautoscaling.model.ScalableDimension;
import com.amazonaws.services.applicationautoscaling.model.ScalableTarget;
import com.amazonaws.services.applicationautoscaling.model.ServiceNamespace;
import com.amazonaws.services.applicationautoscaling.model.SuspendedState;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription.ServiceDiscoveryAssociation;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsResource;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsServerGroupName;
import com.netflix.spinnaker.clouddriver.ecs.names.EcsServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService;
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector;
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector;
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller;
import com.netflix.spinnaker.clouddriver.names.NamerRegistry;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.moniker.Moniker;
import com.netflix.spinnaker.moniker.Namer;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class CreateServerGroupAtomicOperation
    extends AbstractEcsAtomicOperation<CreateServerGroupDescription, DeploymentResult> {

  private static final String NECESSARY_TRUSTED_SERVICE = "ecs-tasks.amazonaws.com";
  protected static final String AWSVPC_NETWORK_MODE = "awsvpc";
  protected static final String HOST_NETWORK_MODE = "host";
  protected static final String EC2 = "EC2";
  protected static final String FARGATE = "FARGATE";
  protected static final String FARGATE_SPOT = "FARGATE_SPOT";
  protected static final String NO_IAM_ROLE = "None (No IAM role)";
  protected static final String NO_IMAGE_CREDENTIALS = "None (No registry credentials)";

  protected static final String DOCKER_LABEL_KEY_SERVERGROUP = "spinnaker.servergroup";
  protected static final String DOCKER_LABEL_KEY_STACK = "spinnaker.stack";
  protected static final String DOCKER_LABEL_KEY_DETAIL = "spinnaker.detail";

  protected ObjectMapper mapper =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Autowired EcsCloudMetricService ecsCloudMetricService;
  @Autowired IamPolicyReader iamPolicyReader;

  @Autowired SubnetSelector subnetSelector;

  @Autowired SecurityGroupSelector securityGroupSelector;

  @Autowired ArtifactDownloader artifactDownloader;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    super(description, "CREATE_ECS_SERVER_GROUP");
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Create Amazon ECS Server Group Operation...");

    AmazonCredentials credentials = getCredentials();

    AmazonECS ecs = getAmazonEcsClient();

    Namer<EcsResource> namer =
        NamerRegistry.lookup()
            .withProvider(EcsCloudProvider.ID)
            .withAccount(credentials.getName())
            .withResource(EcsResource.class);

    EcsServerGroupName newServerGroup = buildEcsServerGroupName(ecs, namer);

    ScalableTarget sourceTarget = getSourceScalableTarget();
    Service sourceService = getSourceService();

    String ecsServiceRole = inferAssumedRoleArn(credentials);

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, ecsServiceRole, newServerGroup);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    Service service = createService(ecs, taskDefinition, newServerGroup, sourceService, namer);

    String resourceId = registerAutoScalingGroup(credentials, service, sourceTarget);

    if (description.isCopySourceScalingPoliciesAndActions() && sourceTarget != null) {
      updateTaskStatus("Copying scaling policies...");
      ecsCloudMetricService.copyScalingPolicies(
          description.getAccount(),
          getRegion(),
          service.getServiceName(),
          resourceId,
          description.getSource().getAccount(),
          description.getSource().getRegion(),
          description.getSource().getAsgName(),
          sourceTarget.getResourceId(),
          description.getEcsClusterName());
      updateTaskStatus("Done copying scaling policies...");
    }

    return makeDeploymentResult(service);
  }

  private EcsServerGroupName buildEcsServerGroupName(AmazonECS ecs, Namer<EcsResource> namer) {
    EcsServerGroupNameResolver serverGroupNameResolver =
        new EcsServerGroupNameResolver(description.getEcsClusterName(), ecs, getRegion(), namer);

    if (description.getMoniker() != null) {
      return serverGroupNameResolver.resolveNextName(description.getMoniker());
    }

    return serverGroupNameResolver.resolveNextName(
        description.getApplication(), description.getStack(), description.getFreeFormDetails());
  }

  protected TaskDefinition registerTaskDefinition(
      AmazonECS ecs, String ecsServiceRole, EcsServerGroupName newServerGroupName) {

    RegisterTaskDefinitionRequest request;

    if (description.isUseTaskDefinitionArtifact()) {
      request = makeTaskDefinitionRequestFromArtifact(ecsServiceRole, newServerGroupName);
    } else {
      request = makeTaskDefinitionRequest(ecsServiceRole, newServerGroupName);
    }

    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(request);

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  protected RegisterTaskDefinitionRequest makeTaskDefinitionRequest(
      String ecsServiceRole, EcsServerGroupName newServerGroupName) {
    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();

    // Set all user defined environment variables
    final Map<String, String> environmentVariables = description.getEnvironmentVariables();
    if (environmentVariables != null) {
      for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
        containerEnvironment.add(
            new KeyValuePair().withName(entry.getKey()).withValue(entry.getValue()));
      }
    }

    containerEnvironment = setSpinnakerEnvVars(containerEnvironment, newServerGroupName);

    ContainerDefinition containerDefinition =
        new ContainerDefinition()
            .withName(newServerGroupName.getContainerName())
            .withEnvironment(containerEnvironment)
            .withCpu(description.getComputeUnits())
            .withMemoryReservation(description.getReservedMemory())
            .withImage(description.getDockerImageAddress());

    Set<PortMapping> portMappings = new HashSet<>();

    if (!StringUtils.isEmpty(description.getTargetGroup())
        && description.getContainerPort() != null) {
      PortMapping portMapping =
          new PortMapping()
              .withProtocol(
                  description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

      if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())
          || HOST_NETWORK_MODE.equals(description.getNetworkMode())) {
        portMapping
            .withHostPort(description.getContainerPort())
            .withContainerPort(description.getContainerPort());
      } else {
        portMapping.withHostPort(0).withContainerPort(description.getContainerPort());
      }

      portMappings.add(portMapping);
    }

    if (description.getTargetGroupMappings() != null) {
      for (CreateServerGroupDescription.TargetGroupProperties properties :
          description.getTargetGroupMappings()) {
        PortMapping portMapping =
            new PortMapping()
                .withProtocol(
                    description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

        if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())
            || HOST_NETWORK_MODE.equals(description.getNetworkMode())) {
          portMapping
              .withHostPort(properties.getContainerPort())
              .withContainerPort(properties.getContainerPort());
        } else {
          portMapping.withHostPort(0).withContainerPort(properties.getContainerPort());
        }

        portMappings.add(portMapping);
      }
    }

    if (description.getServiceDiscoveryAssociations() != null) {
      for (ServiceDiscoveryAssociation config : description.getServiceDiscoveryAssociations()) {
        if (config.getContainerPort() != null
            && config.getContainerPort() != 0
            && config.getContainerPort() != description.getContainerPort()) {
          PortMapping portMapping = new PortMapping().withProtocol("tcp");
          if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
            portMapping
                .withHostPort(config.getContainerPort())
                .withContainerPort(config.getContainerPort());
          } else {
            portMapping.withHostPort(0).withContainerPort(config.getContainerPort());
          }
          portMappings.add(portMapping);
        }
      }
    }

    log.debug("The container port mappings are: {}", portMappings);
    containerDefinition.setPortMappings(portMappings);

    if (!NO_IMAGE_CREDENTIALS.equals(description.getDockerImageCredentialsSecret())
        && description.getDockerImageCredentialsSecret() != null) {
      RepositoryCredentials credentials =
          new RepositoryCredentials()
              .withCredentialsParameter(description.getDockerImageCredentialsSecret());
      containerDefinition.withRepositoryCredentials(credentials);
    }

    Map<String, String> labelsMap = new HashMap<>();
    if (description.getDockerLabels() != null) {
      labelsMap.putAll(description.getDockerLabels());
    }

    labelsMap = setSpinnakerDockerLabels(labelsMap, newServerGroupName);

    containerDefinition.withDockerLabels(labelsMap);

    if (description.getLogDriver() != null && !"None".equals(description.getLogDriver())) {
      LogConfiguration logConfiguration =
          new LogConfiguration()
              .withLogDriver(description.getLogDriver())
              .withOptions(description.getLogOptions());

      containerDefinition.withLogConfiguration(logConfiguration);
    }

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest request =
        new RegisterTaskDefinitionRequest()
            .withContainerDefinitions(containerDefinitions)
            .withFamily(newServerGroupName.getFamilyName());
    if (description.getNetworkMode() != null && !description.getNetworkMode().equals("default")) {
      request.withNetworkMode(description.getNetworkMode());
    }

    if (!NO_IAM_ROLE.equals(description.getIamRole()) && description.getIamRole() != null) {
      request.setTaskRoleArn(checkRoleTrustRelations(description.getIamRole()).getRole().getArn());
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      request.setRequiresCompatibilities(Arrays.asList(description.getLaunchType()));

      if (FARGATE.equals(description.getLaunchType())) {
        request.setExecutionRoleArn(ecsServiceRole);
        request.setCpu(description.getComputeUnits().toString());
        request.setMemory(description.getReservedMemory().toString());
      }
    }

    if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {

      for (CapacityProviderStrategyItem cpStrategy : description.getCapacityProviderStrategy()) {
        if (FARGATE.equals(cpStrategy.getCapacityProvider())
            || FARGATE_SPOT.equals(cpStrategy.getCapacityProvider())) {
          request.setRequiresCompatibilities(Arrays.asList(FARGATE));
          request.setExecutionRoleArn(ecsServiceRole);
          request.setCpu(description.getComputeUnits().toString());
          request.setMemory(description.getReservedMemory().toString());
        }
      }
    }

    return request;
  }

  protected RegisterTaskDefinitionRequest makeTaskDefinitionRequestFromArtifact(
      String ecsServiceRole, EcsServerGroupName newServerGroupName) {

    File artifactFile =
        downloadTaskDefinitionArtifact(description.getResolvedTaskDefinitionArtifact());

    RegisterTaskDefinitionRequest requestTemplate;
    try {
      requestTemplate = mapper.readValue(artifactFile, RegisterTaskDefinitionRequest.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    String templateMode = requestTemplate.getNetworkMode();
    if (templateMode != null
        && !templateMode.isEmpty()
        && !templateMode.equals(description.getNetworkMode())) {
      throw new IllegalArgumentException(
          "Task definition networkMode does not match server group value. Found '"
              + templateMode
              + "' but expected '"
              + description.getNetworkMode()
              + "'");
    }

    List<ContainerDefinition> containers = requestTemplate.getContainerDefinitions();
    if (containers.size() == 0) {
      throw new IllegalArgumentException(
          "Provided task definition does not contain any container definitions.");
    }

    description
        .getContainerToImageMap()
        .forEach(
            (k, v) -> {
              // check if taskDefTemplate contains matching container
              List<ContainerDefinition> matches =
                  containers.stream()
                      .filter(x -> x.getName().equals(k))
                      .collect(Collectors.toList());

              if (matches.size() != 1) {
                throw new IllegalArgumentException(
                    "Invalid number of matching containers found for mapping '"
                        + k
                        + "'. Have "
                        + matches.size()
                        + " but expected 1.");
              }

              // interpolate container mappings
              matches.get(0).setImage(v);
            });

    containers.forEach(
        (c) -> {
          Collection<KeyValuePair> updatedEnv =
              setSpinnakerEnvVars(c.getEnvironment(), newServerGroupName);
          c.setEnvironment(updatedEnv);

          Map<String, String> updatedLabels =
              setSpinnakerDockerLabels(c.getDockerLabels(), newServerGroupName);
          c.setDockerLabels(updatedLabels);
        });

    requestTemplate.setFamily(newServerGroupName.getFamilyName());

    if (FARGATE.equals(description.getLaunchType())) {
      String templateExecutionRole = requestTemplate.getExecutionRoleArn();

      if (templateExecutionRole == null || templateExecutionRole.isEmpty()) {
        requestTemplate.setExecutionRoleArn(ecsServiceRole);
      }
    } else if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {
      for (CapacityProviderStrategyItem cpStrategy : description.getCapacityProviderStrategy()) {
        if (FARGATE.equals(cpStrategy.getCapacityProvider())
            || FARGATE_SPOT.equals(cpStrategy.getCapacityProvider())) {
          String templateExecutionRole = requestTemplate.getExecutionRoleArn();

          if (templateExecutionRole == null || StringUtils.isBlank(templateExecutionRole)) {
            requestTemplate.setExecutionRoleArn(ecsServiceRole);
          }

          return requestTemplate;
        }
      }
    }

    return requestTemplate;
  }

  private File downloadTaskDefinitionArtifact(Artifact taskDefArtifact) {
    File file = null;
    if (taskDefArtifact.getArtifactAccount() == null
        || taskDefArtifact.getArtifactAccount().isEmpty()
            && description.getTaskDefinitionArtifactAccount() != null
            && !description.getTaskDefinitionArtifactAccount().isEmpty()) {
      taskDefArtifact =
          taskDefArtifact.toBuilder()
              .artifactAccount(description.getTaskDefinitionArtifactAccount())
              .build();
    }
    try {
      InputStream artifactInput = artifactDownloader.download(taskDefArtifact);
      file = File.createTempFile(UUID.randomUUID().toString(), null);
      FileOutputStream fileOutputStream = new FileOutputStream(file);
      IOUtils.copy(artifactInput, fileOutputStream);
      fileOutputStream.close();
    } catch (IOException e) {
      if (file != null) {
        file.delete();
      }
      throw new UncheckedIOException(e);
    }
    return file;
  }

  private Service createService(
      AmazonECS ecs,
      TaskDefinition taskDefinition,
      EcsServerGroupName newServerGroupName,
      Service sourceService,
      Namer<EcsResource> namer) {

    String taskDefinitionArn = taskDefinition.getTaskDefinitionArn();

    Integer desiredCount = description.getCapacity().getDesired();
    if (sourceService != null
        && description.getSource() != null
        && description.getSource().getUseSourceCapacity() != null
        && description.getSource().getUseSourceCapacity()) {
      desiredCount = sourceService.getDesiredCount();
    }

    CreateServiceRequest request =
        makeServiceRequest(
            taskDefinitionArn, newServerGroupName, desiredCount, namer, isTaggingEnabled(ecs));

    updateTaskStatus(
        String.format(
            "Creating %s of %s with %s for %s.",
            desiredCount, newServerGroupName, taskDefinitionArn, description.getAccount()));

    log.debug("CreateServiceRequest being made is: {}", request.toString());

    Service service = ecs.createService(request).getService();

    updateTaskStatus(
        String.format(
            "Done creating %s of %s with %s for %s.",
            desiredCount, newServerGroupName, taskDefinitionArn, description.getAccount()));

    return service;
  }

  protected CreateServiceRequest makeServiceRequest(
      String taskDefinitionArn,
      EcsServerGroupName newServerGroupName,
      Integer desiredCount,
      Namer<EcsResource> namer,
      boolean taggingEnabled) {
    Collection<ServiceRegistry> serviceRegistries = new LinkedList<>();
    if (description.getServiceDiscoveryAssociations() != null) {
      for (ServiceDiscoveryAssociation config : description.getServiceDiscoveryAssociations()) {
        ServiceRegistry registryEntry =
            new ServiceRegistry().withRegistryArn(config.getRegistry().getArn());

        if (config.getContainerPort() != null && config.getContainerPort() != 0) {
          registryEntry.setContainerPort(config.getContainerPort());

          if (StringUtils.isEmpty(config.getContainerName())) {
            registryEntry.setContainerName(newServerGroupName.getContainerName());
          } else {
            registryEntry.setContainerName(config.getContainerName());
          }
        }

        serviceRegistries.add(registryEntry);
      }
    }

    DeploymentConfiguration deploymentConfiguration =
        new DeploymentConfiguration().withMinimumHealthyPercent(100).withMaximumPercent(200);

    CreateServiceRequest request =
        new CreateServiceRequest()
            .withServiceName(newServerGroupName.getServiceName())
            .withDesiredCount(desiredCount)
            .withCluster(description.getEcsClusterName())
            .withTaskDefinition(taskDefinitionArn)
            .withPlacementConstraints(description.getPlacementConstraints())
            .withPlacementStrategy(description.getPlacementStrategySequence())
            .withServiceRegistries(serviceRegistries)
            .withDeploymentConfiguration(deploymentConfiguration);

    List<Tag> taskDefTags = new LinkedList<>();
    if (description.getTags() != null && !description.getTags().isEmpty()) {
      for (Map.Entry<String, String> entry : description.getTags().entrySet()) {
        taskDefTags.add(new Tag().withKey(entry.getKey()).withValue(entry.getValue()));
      }
    }

    // Apply moniker strategy which may add tags
    namer.applyMoniker(
        new EcsResource() {
          @Override
          public String getName() {
            return request.getServiceName();
          }

          // Used by Frigga when moniker support is disabled
          public void setName(String name) {
            request.setServiceName(name);
          }

          @Override
          public List<Tag> getResourceTags() {
            return taskDefTags;
          }
        },
        newServerGroupName.getMoniker());

    // Only add tags if they're set as it's an optional feature for ECS
    if (taggingEnabled) {
      request
          .withTags(taskDefTags)
          .withEnableECSManagedTags(true)
          .withPropagateTags(PropagateTags.SERVICE.toString());
    } else {
      if (!taskDefTags.isEmpty()) {
        throw new IllegalArgumentException(
            "ECS account settings for account "
                + description.getAccount()
                + " do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled.");
      }
    }

    request.withLoadBalancers(retrieveLoadBalancers(newServerGroupName.getContainerName()));

    if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
      Collection<String> subnetIds =
          subnetSelector.resolveSubnetsIdsForMultipleSubnetTypes(
              description.getAccount(),
              description.getRegion(),
              description.getAvailabilityZones().get(description.getRegion()),
              getSubnetTypes());
      Collection<String> vpcIds =
          subnetSelector.getSubnetVpcIds(
              description.getAccount(), description.getRegion(), subnetIds);
      Collection<String> securityGroupIds =
          securityGroupSelector.resolveSecurityGroupNames(
              description.getAccount(),
              description.getRegion(),
              description.getSecurityGroupNames(),
              vpcIds);

      AwsVpcConfiguration awsvpcConfiguration =
          new AwsVpcConfiguration().withSecurityGroups(securityGroupIds).withSubnets(subnetIds);

      if (description.getAssociatePublicIpAddress() != null) {
        awsvpcConfiguration.withAssignPublicIp(
            description.getAssociatePublicIpAddress() ? "ENABLED" : "DISABLED");
      }

      request.withNetworkConfiguration(
          new NetworkConfiguration().withAwsvpcConfiguration(awsvpcConfiguration));
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      request.withLaunchType(description.getLaunchType());
    } else if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {
      request.withCapacityProviderStrategy(description.getCapacityProviderStrategy());
    }

    if (!StringUtils.isEmpty(description.getPlatformVersion())) {
      request.withPlatformVersion(description.getPlatformVersion());
    }

    if (description.getHealthCheckGracePeriodSeconds() != null) {
      request.withHealthCheckGracePeriodSeconds(description.getHealthCheckGracePeriodSeconds());
    }

    return request;
  }

  private boolean isTaggingEnabled(AmazonECS ecs) {
    Set<String> enabledSettings =
        ecs
            .listAccountSettings(new ListAccountSettingsRequest().withEffectiveSettings(true))
            .getSettings()
            .stream()
            .filter(e -> Objects.equals("enabled", e.getValue()))
            .map(Setting::getName)
            .collect(Collectors.toSet());

    return enabledSettings.containsAll(
        Set.of(
            SettingName.ServiceLongArnFormat.toString(), SettingName.TaskLongArnFormat.toString()));
  }

  private String registerAutoScalingGroup(
      AmazonCredentials credentials, Service service, ScalableTarget sourceTarget) {

    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();

    Integer min = description.getCapacity().getMin();
    Integer max = description.getCapacity().getMax();

    if (sourceTarget != null
        && description.getSource() != null
        && description.getSource().getUseSourceCapacity() != null
        && description.getSource().getUseSourceCapacity()) {
      min = sourceTarget.getMinCapacity();
      max = sourceTarget.getMaxCapacity();
    }

    RegisterScalableTargetRequest request =
        new RegisterScalableTargetRequest()
            .withServiceNamespace(ServiceNamespace.Ecs)
            .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
            .withResourceId(
                String.format(
                    "service/%s/%s", description.getEcsClusterName(), service.getServiceName()))
            .withMinCapacity(min)
            .withMaxCapacity(max)
            .withSuspendedState(
                new SuspendedState()
                    .withDynamicScalingInSuspended(false)
                    .withDynamicScalingOutSuspended(false)
                    .withScheduledScalingSuspended(false));

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    // ECS DescribeService is eventually consistent, so sometimes RegisterScalableTarget will
    // return a ValidationException with message "ECS service doesn't exist", because the service
    // was just created.  Retry until consistency is likely reached.
    OperationPoller.retryWithBackoff(
        o -> autoScalingClient.registerScalableTarget(request), 1000, 3);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition.");

    return request.getResourceId();
  }

  private ScalableTarget getSourceScalableTarget() {
    if (description.getSource() != null
        && description.getSource().getRegion() != null
        && description.getSource().getAccount() != null
        && description.getSource().getAsgName() != null) {

      AWSApplicationAutoScaling autoScalingClient = getSourceAmazonApplicationAutoScalingClient();

      DescribeScalableTargetsRequest request =
          new DescribeScalableTargetsRequest()
              .withServiceNamespace(ServiceNamespace.Ecs)
              .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
              .withResourceIds(
                  String.format(
                      "service/%s/%s",
                      description.getEcsClusterName(), description.getSource().getAsgName()));

      DescribeScalableTargetsResult result = autoScalingClient.describeScalableTargets(request);
      if (result.getScalableTargets() != null && !result.getScalableTargets().isEmpty()) {
        return result.getScalableTargets().get(0);
      }

      return null;
    }

    return null;
  }

  private Service getSourceService() {
    if (description.getSource() != null
        && description.getSource().getRegion() != null
        && description.getSource().getAccount() != null
        && description.getSource().getAsgName() != null) {

      AmazonECS ecsClient = getSourceAmazonEcsClient();

      DescribeServicesRequest request =
          new DescribeServicesRequest()
              .withCluster(description.getEcsClusterName())
              .withServices(description.getSource().getAsgName());

      DescribeServicesResult result = ecsClient.describeServices(request);
      if (result.getServices() != null && !result.getServices().isEmpty()) {
        return result.getServices().get(0);
      }

      return null;
    }

    return null;
  }

  private String inferAssumedRoleArn(AmazonCredentials credentials) {
    String role;
    if (credentials instanceof AssumeRoleAmazonCredentials) {
      role = ((AssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleAmazonCredentials) {
      role = ((NetflixAssumeRoleAmazonCredentials) credentials).getAssumeRole();
    } else if (credentials instanceof NetflixAssumeRoleEcsCredentials) {
      role = ((NetflixAssumeRoleEcsCredentials) credentials).getAssumeRole();
    } else {
      throw new UnsupportedOperationException(
          "The given kind of credentials is not supported, "
              + "please report this issue to the Spinnaker project on Github.");
    }
    if (!role.startsWith("arn:")) {
      return String.format("arn:aws:iam::%s:%s", credentials.getAccountId(), role);
    }
    return role;
  }

  private GetRoleResult checkRoleTrustRelations(String roleName) {
    updateTaskStatus("Checking role trust relations for: " + roleName);
    AmazonIdentityManagement iamClient = getAmazonIdentityManagementClient();

    GetRoleResult response = iamClient.getRole(new GetRoleRequest().withRoleName(roleName));
    Role role = response.getRole();

    Set<IamTrustRelationship> trustedEntities =
        iamPolicyReader.getTrustedEntities(role.getAssumeRolePolicyDocument());

    Set<String> trustedServices =
        trustedEntities.stream()
            .filter(trustRelation -> trustRelation.getType().equals("Service"))
            .map(IamTrustRelationship::getValue)
            .collect(Collectors.toSet());

    if (!trustedServices.contains(NECESSARY_TRUSTED_SERVICE)) {
      throw new IllegalArgumentException(
          "The "
              + roleName
              + " role does not have a trust relationship to ecs-tasks.amazonaws.com.");
    }
    return response;
  }

  private Set<String> getSubnetTypes() {
    Set<String> subnetTypes = new HashSet<>();

    if (description.getSubnetTypes() != null && !description.getSubnetTypes().isEmpty()) {
      subnetTypes.addAll(description.getSubnetTypes());
    }

    if (StringUtils.isNotBlank(description.getSubnetType())) {
      subnetTypes.add(description.getSubnetType());
    }
    return subnetTypes;
  }

  private DeploymentResult makeDeploymentResult(Service service) {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), service.getServiceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Arrays.asList(getServerGroupName(service)));
    result.setServerGroupNameByRegion(namesByRegion);
    return result;
  }

  private Collection<LoadBalancer> retrieveLoadBalancers(String containerName) {
    Set<LoadBalancer> loadBalancers = new HashSet<>();
    Set<CreateServerGroupDescription.TargetGroupProperties> targetGroupMappings = new HashSet<>();

    if (description.getTargetGroupMappings() != null
        && !description.getTargetGroupMappings().isEmpty()) {
      targetGroupMappings.addAll(description.getTargetGroupMappings());
    }

    if (StringUtils.isNotBlank(description.getTargetGroup())) {
      CreateServerGroupDescription.TargetGroupProperties targetGroupMapping =
          new CreateServerGroupDescription.TargetGroupProperties();

      String containerToUse =
          StringUtils.isNotBlank(description.getLoadBalancedContainer())
              ? description.getLoadBalancedContainer()
              : containerName;

      targetGroupMapping.setContainerName(containerToUse);
      targetGroupMapping.setContainerPort(description.getContainerPort());
      targetGroupMapping.setTargetGroup(description.getTargetGroup());

      targetGroupMappings.add(targetGroupMapping);
    }

    for (CreateServerGroupDescription.TargetGroupProperties targetGroupAssociation :
        targetGroupMappings) {
      LoadBalancer loadBalancer = new LoadBalancer();

      String containerToUse =
          StringUtils.isNotBlank(targetGroupAssociation.getContainerName())
              ? targetGroupAssociation.getContainerName()
              : containerName;

      loadBalancer.setContainerName(containerToUse);
      loadBalancer.setContainerPort(targetGroupAssociation.getContainerPort());

      AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();

      DescribeTargetGroupsRequest request =
          new DescribeTargetGroupsRequest().withNames(targetGroupAssociation.getTargetGroup());
      DescribeTargetGroupsResult describeTargetGroupsResult =
          loadBalancingV2.describeTargetGroups(request);

      if (describeTargetGroupsResult.getTargetGroups().size() == 1) {
        loadBalancer.setTargetGroupArn(
            describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn());
      } else if (describeTargetGroupsResult.getTargetGroups().size() > 1) {
        throw new IllegalArgumentException(
            "There are multiple target groups with the name "
                + targetGroupAssociation.getTargetGroup()
                + ".");
      } else {
        throw new IllegalArgumentException(
            "There is no target group with the name "
                + targetGroupAssociation.getTargetGroup()
                + ".");
      }

      loadBalancers.add(loadBalancer);
    }

    return loadBalancers;
  }

  private AWSApplicationAutoScaling getSourceAmazonApplicationAutoScalingClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials =
        credentialsRepository.getOne(description.getSource().getAccount());
    return amazonClientProvider.getAmazonApplicationAutoScaling(
        sourceCredentials, sourceRegion, false);
  }

  private AmazonECS getSourceAmazonEcsClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials =
        credentialsRepository.getOne(description.getSource().getAccount());
    return amazonClientProvider.getAmazonEcs(sourceCredentials, sourceRegion, false);
  }

  private AmazonElasticLoadBalancing getAmazonElasticLoadBalancingClient() {
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonElasticLoadBalancingV2(
        credentialAccount, getRegion(), false);
  }

  private AmazonIdentityManagement getAmazonIdentityManagementClient() {
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonIdentityManagement(credentialAccount, getRegion(), false);
  }

  private String getServerGroupName(Service service) {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + service.getServiceName();
  }

  private Collection<KeyValuePair> setSpinnakerEnvVars(
      Collection<KeyValuePair> targetEnv, EcsServerGroupName newServerGroupName) {

    Moniker moniker = newServerGroupName.getMoniker();

    targetEnv.add(
        new KeyValuePair().withName("SERVER_GROUP").withValue(newServerGroupName.getServiceName()));
    targetEnv.add(new KeyValuePair().withName("CLOUD_STACK").withValue(moniker.getStack()));
    targetEnv.add(new KeyValuePair().withName("CLOUD_DETAIL").withValue(moniker.getDetail()));

    return targetEnv;
  }

  private Map<String, String> setSpinnakerDockerLabels(
      Map<String, String> targetMap, EcsServerGroupName newServerGroupName) {

    Map<String, String> newLabels = new HashMap<>();
    if (targetMap != null) {
      newLabels.putAll(targetMap);
    }

    Moniker moniker = newServerGroupName.getMoniker();

    if (StringUtils.isNotBlank(moniker.getStack())) {
      newLabels.put(DOCKER_LABEL_KEY_STACK, moniker.getStack());
    }

    if (StringUtils.isNotBlank(moniker.getDetail())) {
      newLabels.put(DOCKER_LABEL_KEY_DETAIL, moniker.getDetail());
    }

    newLabels.put(DOCKER_LABEL_KEY_SERVERGROUP, newServerGroupName.getServiceName());

    return newLabels;
  }

  @Override
  protected String getRegion() {
    // CreateServerGroupDescription does not contain a region. Instead it has AvailabilityZones
    return description.getAvailabilityZones().keySet().iterator().next();
  }
}
