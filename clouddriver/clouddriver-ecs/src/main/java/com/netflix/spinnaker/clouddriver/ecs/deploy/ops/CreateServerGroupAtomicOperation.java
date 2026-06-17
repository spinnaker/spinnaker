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
import software.amazon.awssdk.services.applicationautoscaling.ApplicationAutoScalingClient;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.DescribeScalableTargetsResponse;
import software.amazon.awssdk.services.applicationautoscaling.model.RegisterScalableTargetRequest;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableDimension;
import software.amazon.awssdk.services.applicationautoscaling.model.ScalableTarget;
import software.amazon.awssdk.services.applicationautoscaling.model.ServiceNamespace;
import software.amazon.awssdk.services.applicationautoscaling.model.SuspendedState;
import software.amazon.awssdk.services.ecs.EcsClient;
import software.amazon.awssdk.services.ecs.model.*;
import software.amazon.awssdk.services.elasticloadbalancingv2.ElasticLoadBalancingV2Client;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import software.amazon.awssdk.services.elasticloadbalancingv2.model.DescribeTargetGroupsResponse;
import software.amazon.awssdk.services.iam.IamClient;
import software.amazon.awssdk.services.iam.model.GetRoleRequest;
import software.amazon.awssdk.services.iam.model.GetRoleResponse;
import software.amazon.awssdk.services.iam.model.Role;

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

    EcsClient ecs = getAmazonEcsClient();

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
          service.serviceName(),
          resourceId,
          description.getSource().getAccount(),
          description.getSource().getRegion(),
          description.getSource().getAsgName(),
          sourceTarget.resourceId(),
          description.getEcsClusterName());
      updateTaskStatus("Done copying scaling policies...");
    }

    return makeDeploymentResult(service);
  }

  private EcsServerGroupName buildEcsServerGroupName(EcsClient ecs, Namer<EcsResource> namer) {
    EcsClient ecsV2 =
        amazonClientProvider.getAmazonEcsV2(description.getCredentials(), getRegion());
    EcsServerGroupNameResolver serverGroupNameResolver =
        new EcsServerGroupNameResolver(description.getEcsClusterName(), ecsV2, getRegion(), namer);

    if (description.getMoniker() != null) {
      return serverGroupNameResolver.resolveNextName(description.getMoniker());
    }

    return serverGroupNameResolver.resolveNextName(
        description.getApplication(), description.getStack(), description.getFreeFormDetails());
  }

  protected TaskDefinition registerTaskDefinition(
      EcsClient ecs, String ecsServiceRole, EcsServerGroupName newServerGroupName) {

    RegisterTaskDefinitionRequest request;

    if (description.isUseTaskDefinitionArtifact()) {
      request = makeTaskDefinitionRequestFromArtifact(ecsServiceRole, newServerGroupName);
    } else {
      request = makeTaskDefinitionRequest(ecsServiceRole, newServerGroupName);
    }

    RegisterTaskDefinitionResponse registerTaskDefinitionResult =
        ecs.registerTaskDefinition(request);

    return registerTaskDefinitionResult.taskDefinition();
  }

  protected RegisterTaskDefinitionRequest makeTaskDefinitionRequest(
      String ecsServiceRole, EcsServerGroupName newServerGroupName) {
    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();

    // Set all user defined environment variables
    final Map<String, String> environmentVariables = description.getEnvironmentVariables();
    if (environmentVariables != null) {
      for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
        containerEnvironment.add(
            KeyValuePair.builder().name(entry.getKey()).value(entry.getValue()).build());
      }
    }

    containerEnvironment = setSpinnakerEnvVars(containerEnvironment, newServerGroupName);

    ContainerDefinition.Builder containerDefinitionBuilder =
        ContainerDefinition.builder()
            .name(newServerGroupName.getContainerName())
            .environment(containerEnvironment)
            .cpu(description.getComputeUnits())
            .memoryReservation(description.getReservedMemory())
            .image(description.getDockerImageAddress());

    Set<PortMapping> portMappings = new HashSet<>();

    if (!StringUtils.isEmpty(description.getTargetGroup())
        && description.getContainerPort() != null) {
      PortMapping.Builder portMappingBuilder =
          PortMapping.builder()
              .protocol(
                  description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

      if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())
          || HOST_NETWORK_MODE.equals(description.getNetworkMode())) {
        portMappingBuilder
            .hostPort(description.getContainerPort())
            .containerPort(description.getContainerPort());
      } else {
        portMappingBuilder.hostPort(0).containerPort(description.getContainerPort());
      }

      portMappings.add(portMappingBuilder.build());
    }

    if (description.getTargetGroupMappings() != null) {
      for (CreateServerGroupDescription.TargetGroupProperties properties :
          description.getTargetGroupMappings()) {
        PortMapping.Builder portMappingBuilder =
            PortMapping.builder()
                .protocol(
                    description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

        if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())
            || HOST_NETWORK_MODE.equals(description.getNetworkMode())) {
          portMappingBuilder
              .hostPort(properties.getContainerPort())
              .containerPort(properties.getContainerPort());
        } else {
          portMappingBuilder.hostPort(0).containerPort(properties.getContainerPort());
        }

        portMappings.add(portMappingBuilder.build());
      }
    }

    if (description.getServiceDiscoveryAssociations() != null) {
      for (ServiceDiscoveryAssociation config : description.getServiceDiscoveryAssociations()) {
        if (config.getContainerPort() != null
            && config.getContainerPort() != 0
            && config.getContainerPort() != description.getContainerPort()) {
          PortMapping.Builder portMappingBuilder = PortMapping.builder().protocol("tcp");
          if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
            portMappingBuilder
                .hostPort(config.getContainerPort())
                .containerPort(config.getContainerPort());
          } else {
            portMappingBuilder.hostPort(0).containerPort(config.getContainerPort());
          }
          portMappings.add(portMappingBuilder.build());
        }
      }
    }

    log.debug("The container port mappings are: {}", portMappings);
    containerDefinitionBuilder.portMappings(portMappings);

    if (!NO_IMAGE_CREDENTIALS.equals(description.getDockerImageCredentialsSecret())
        && description.getDockerImageCredentialsSecret() != null) {
      RepositoryCredentials credentials =
          RepositoryCredentials.builder()
              .credentialsParameter(description.getDockerImageCredentialsSecret())
              .build();
      containerDefinitionBuilder.repositoryCredentials(credentials);
    }

    Map<String, String> labelsMap = new HashMap<>();
    if (description.getDockerLabels() != null) {
      labelsMap.putAll(description.getDockerLabels());
    }

    labelsMap = setSpinnakerDockerLabels(labelsMap, newServerGroupName);

    containerDefinitionBuilder.dockerLabels(labelsMap);

    if (description.getLogDriver() != null && !"None".equals(description.getLogDriver())) {
      LogConfiguration logConfiguration =
          LogConfiguration.builder()
              .logDriver(description.getLogDriver())
              .options(description.getLogOptions())
              .build();

      containerDefinitionBuilder.logConfiguration(logConfiguration);
    }

    ContainerDefinition containerDefinition = containerDefinitionBuilder.build();

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest.Builder requestBuilder =
        RegisterTaskDefinitionRequest.builder()
            .containerDefinitions(containerDefinitions)
            .family(newServerGroupName.getFamilyName());
    if (description.getNetworkMode() != null && !description.getNetworkMode().equals("default")) {
      requestBuilder.networkMode(description.getNetworkMode());
    }

    if (!NO_IAM_ROLE.equals(description.getIamRole()) && description.getIamRole() != null) {
      requestBuilder.taskRoleArn(checkRoleTrustRelations(description.getIamRole()).role().arn());
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      requestBuilder.requiresCompatibilities(Compatibility.fromValue(description.getLaunchType()));

      if (FARGATE.equals(description.getLaunchType())) {
        requestBuilder.executionRoleArn(ecsServiceRole);
        requestBuilder.cpu(description.getComputeUnits().toString());
        requestBuilder.memory(description.getReservedMemory().toString());
      }
    }

    if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {

      for (CapacityProviderStrategyItem cpStrategy : description.getCapacityProviderStrategy()) {
        if (FARGATE.equals(cpStrategy.capacityProvider())
            || FARGATE_SPOT.equals(cpStrategy.capacityProvider())) {
          requestBuilder.requiresCompatibilities(Compatibility.FARGATE);
          requestBuilder.executionRoleArn(ecsServiceRole);
          requestBuilder.cpu(description.getComputeUnits().toString());
          requestBuilder.memory(description.getReservedMemory().toString());
        }
      }
    }

    return requestBuilder.build();
  }

  private RegisterTaskDefinitionRequest getSpelProcessedArtifact() {
    if (description.getSpelProcessedTaskDefinitionArtifact() != null) {
      return mapper.convertValue(
          description.getSpelProcessedTaskDefinitionArtifact(),
          RegisterTaskDefinitionRequest.class);
    } else {
      throw new IllegalArgumentException("Task definition artifact can not be null");
    }
  }

  private RegisterTaskDefinitionRequest getArtifactFromFile() {
    File artifactFile =
        downloadTaskDefinitionArtifact(description.getResolvedTaskDefinitionArtifact());
    try {
      return mapper.readValue(artifactFile, RegisterTaskDefinitionRequest.class);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  protected RegisterTaskDefinitionRequest makeTaskDefinitionRequestFromArtifact(
      String ecsServiceRole, EcsServerGroupName newServerGroupName) {

    RegisterTaskDefinitionRequest requestTemplate = null;
    if (description.isEvaluateTaskDefinitionArtifactExpressions()) {
      requestTemplate = getSpelProcessedArtifact();
    } else {
      requestTemplate = getArtifactFromFile();
    }

    String templateMode = requestTemplate.networkModeAsString();
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

    List<ContainerDefinition> containers = requestTemplate.containerDefinitions();
    if (containers.size() == 0) {
      throw new IllegalArgumentException(
          "Provided task definition does not contain any container definitions.");
    }

    // We need to rebuild containers with updated images and env vars
    List<ContainerDefinition> updatedContainers = new ArrayList<>(containers);

    description
        .getContainerToImageMap()
        .forEach(
            (k, v) -> {
              // check if taskDefTemplate contains matching container
              List<ContainerDefinition> matches =
                  updatedContainers.stream()
                      .filter(x -> x.name().equals(k))
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
              int idx = updatedContainers.indexOf(matches.get(0));
              updatedContainers.set(idx, matches.get(0).toBuilder().image(v).build());
            });

    // Update env vars and docker labels on each container
    for (int i = 0; i < updatedContainers.size(); i++) {
      ContainerDefinition c = updatedContainers.get(i);
      Collection<KeyValuePair> updatedEnv =
          setSpinnakerEnvVars(new LinkedList<>(c.environment()), newServerGroupName);
      Map<String, String> updatedLabels =
          setSpinnakerDockerLabels(c.dockerLabels(), newServerGroupName);
      updatedContainers.set(
          i, c.toBuilder().environment(updatedEnv).dockerLabels(updatedLabels).build());
    }

    RegisterTaskDefinitionRequest.Builder builder =
        requestTemplate.toBuilder()
            .family(newServerGroupName.getFamilyName())
            .containerDefinitions(updatedContainers);

    if (FARGATE.equals(description.getLaunchType())) {
      String templateExecutionRole = requestTemplate.executionRoleArn();

      if (templateExecutionRole == null || templateExecutionRole.isEmpty()) {
        builder.executionRoleArn(ecsServiceRole);
      }
    } else if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {
      for (CapacityProviderStrategyItem cpStrategy : description.getCapacityProviderStrategy()) {
        if (FARGATE.equals(cpStrategy.capacityProvider())
            || FARGATE_SPOT.equals(cpStrategy.capacityProvider())) {
          String templateExecutionRole = requestTemplate.executionRoleArn();

          if (templateExecutionRole == null || StringUtils.isBlank(templateExecutionRole)) {
            builder.executionRoleArn(ecsServiceRole);
          }

          return builder.build();
        }
      }
    }

    return builder.build();
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
      EcsClient ecs,
      TaskDefinition taskDefinition,
      EcsServerGroupName newServerGroupName,
      Service sourceService,
      Namer<EcsResource> namer) {

    String taskDefinitionArn = taskDefinition.taskDefinitionArn();

    Integer desiredCount = description.getCapacity().getDesired();
    if (sourceService != null
        && description.getSource() != null
        && description.getSource().getUseSourceCapacity() != null
        && description.getSource().getUseSourceCapacity()) {
      desiredCount = sourceService.desiredCount();
    }

    CreateServiceRequest request =
        makeServiceRequest(
            taskDefinitionArn, newServerGroupName, desiredCount, namer, isTaggingEnabled(ecs));

    updateTaskStatus(
        String.format(
            "Creating %s of %s with %s for %s.",
            desiredCount, newServerGroupName, taskDefinitionArn, description.getAccount()));

    log.debug("CreateServiceRequest being made is: {}", request.toString());

    Service service = ecs.createService(request).service();

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
        ServiceRegistry.Builder registryBuilder =
            ServiceRegistry.builder().registryArn(config.getRegistry().getArn());

        if (config.getContainerPort() != null && config.getContainerPort() != 0) {
          registryBuilder.containerPort(config.getContainerPort());

          if (StringUtils.isEmpty(config.getContainerName())) {
            registryBuilder.containerName(newServerGroupName.getContainerName());
          } else {
            registryBuilder.containerName(config.getContainerName());
          }
        }

        serviceRegistries.add(registryBuilder.build());
      }
    }

    DeploymentConfiguration deploymentConfiguration =
        DeploymentConfiguration.builder()
            .minimumHealthyPercent(100)
            .maximumPercent(200)
            .deploymentCircuitBreaker(
                DeploymentCircuitBreaker.builder()
                    .enable(description.isEnableDeploymentCircuitBreaker())
                    .rollback(false)
                    .build())
            .build();

    CreateServiceRequest.Builder requestBuilder =
        CreateServiceRequest.builder()
            .serviceName(newServerGroupName.getServiceName())
            .desiredCount(desiredCount)
            .cluster(description.getEcsClusterName())
            .taskDefinition(taskDefinitionArn)
            .placementConstraints(description.getPlacementConstraints())
            .placementStrategy(description.getPlacementStrategySequence())
            .serviceRegistries(serviceRegistries)
            .deploymentConfiguration(deploymentConfiguration)
            .enableExecuteCommand(description.isEnableExecuteCommand());

    List<Tag> taskDefTags = new LinkedList<>();
    if (description.getTags() != null && !description.getTags().isEmpty()) {
      for (Map.Entry<String, String> entry : description.getTags().entrySet()) {
        taskDefTags.add(Tag.builder().key(entry.getKey()).value(entry.getValue()).build());
      }
    }

    // Apply moniker strategy which may add tags
    namer.applyMoniker(
        new EcsResource() {
          @Override
          public String getName() {
            return newServerGroupName.getServiceName();
          }

          // Used by Frigga when moniker support is disabled
          public void setName(String name) {
            requestBuilder.serviceName(name);
          }

          @Override
          public List<Tag> getResourceTags() {
            return taskDefTags;
          }
        },
        newServerGroupName.getMoniker());

    // Only add tags if they're set as it's an optional feature for ECS
    if (taggingEnabled) {
      requestBuilder
          .tags(taskDefTags)
          .enableECSManagedTags(true)
          .propagateTags(PropagateTags.SERVICE);
    } else {
      if (!taskDefTags.isEmpty()) {
        throw new IllegalArgumentException(
            "ECS account settings for account "
                + description.getAccount()
                + " do not allow tagging as `serviceLongArnFormat` and `taskLongArnFormat` are not enabled.");
      }
    }

    requestBuilder.loadBalancers(retrieveLoadBalancers(newServerGroupName.getContainerName()));

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

      AwsVpcConfiguration.Builder awsvpcBuilder =
          AwsVpcConfiguration.builder().securityGroups(securityGroupIds).subnets(subnetIds);

      if (description.getAssociatePublicIpAddress() != null) {
        awsvpcBuilder.assignPublicIp(
            description.getAssociatePublicIpAddress()
                ? AssignPublicIp.ENABLED
                : AssignPublicIp.DISABLED);
      }

      requestBuilder.networkConfiguration(
          NetworkConfiguration.builder().awsvpcConfiguration(awsvpcBuilder.build()).build());
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      requestBuilder.launchType(description.getLaunchType());
    } else if (description.getCapacityProviderStrategy() != null
        && !description.getCapacityProviderStrategy().isEmpty()) {
      requestBuilder.capacityProviderStrategy(description.getCapacityProviderStrategy());
    }

    if (!StringUtils.isEmpty(description.getPlatformVersion())) {
      requestBuilder.platformVersion(description.getPlatformVersion());
    }

    if (description.getHealthCheckGracePeriodSeconds() != null) {
      requestBuilder.healthCheckGracePeriodSeconds(description.getHealthCheckGracePeriodSeconds());
    }

    return requestBuilder.build();
  }

  private boolean isTaggingEnabled(EcsClient ecs) {
    boolean isServiceLongArnFormatEnabled = false;
    boolean isTaskLongArnFormatEnabled = false;

    String nextToken = null;
    do {
      ListAccountSettingsRequest request =
          ListAccountSettingsRequest.builder().effectiveSettings(true).nextToken(nextToken).build();

      ListAccountSettingsResponse response = ecs.listAccountSettings(request);

      for (Setting setting : response.settings()) {
        if (setting.name().equals(SettingName.SERVICE_LONG_ARN_FORMAT)
            && setting.value().equals("enabled")) {
          isServiceLongArnFormatEnabled = true;
        }

        if (setting.name().equals(SettingName.TASK_LONG_ARN_FORMAT)
            && setting.value().equals("enabled")) {
          isTaskLongArnFormatEnabled = true;
        }
      }

      nextToken = response.nextToken();
    } while (nextToken != null);

    return isServiceLongArnFormatEnabled && isTaskLongArnFormatEnabled;
  }

  private String registerAutoScalingGroup(
      AmazonCredentials credentials, Service service, ScalableTarget sourceTarget) {

    ApplicationAutoScalingClient autoScalingClient = getAmazonApplicationAutoScalingClient();

    Integer min = description.getCapacity().getMin();
    Integer max = description.getCapacity().getMax();

    if (sourceTarget != null
        && description.getSource() != null
        && description.getSource().getUseSourceCapacity() != null
        && description.getSource().getUseSourceCapacity()) {
      min = sourceTarget.minCapacity();
      max = sourceTarget.maxCapacity();
    }

    String resourceId =
        String.format("service/%s/%s", description.getEcsClusterName(), service.serviceName());

    RegisterScalableTargetRequest request =
        RegisterScalableTargetRequest.builder()
            .serviceNamespace(ServiceNamespace.ECS)
            .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
            .resourceId(resourceId)
            .minCapacity(min)
            .maxCapacity(max)
            .suspendedState(
                SuspendedState.builder()
                    .dynamicScalingInSuspended(false)
                    .dynamicScalingOutSuspended(false)
                    .scheduledScalingSuspended(false)
                    .build())
            .build();

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    // ECS DescribeService is eventually consistent, so sometimes RegisterScalableTarget will
    // return a ValidationException with message "ECS service doesn't exist", because the service
    // was just created.  Retry until consistency is likely reached.
    OperationPoller.retryWithBackoff(
        o -> autoScalingClient.registerScalableTarget(request), 1000, 3);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition.");

    return resourceId;
  }

  private ScalableTarget getSourceScalableTarget() {
    if (description.getSource() != null
        && description.getSource().getRegion() != null
        && description.getSource().getAccount() != null
        && description.getSource().getAsgName() != null) {

      ApplicationAutoScalingClient autoScalingClient =
          getSourceAmazonApplicationAutoScalingClient();

      DescribeScalableTargetsRequest request =
          DescribeScalableTargetsRequest.builder()
              .serviceNamespace(ServiceNamespace.ECS)
              .scalableDimension(ScalableDimension.ECS_SERVICE_DESIRED_COUNT)
              .resourceIds(
                  String.format(
                      "service/%s/%s",
                      description.getEcsClusterName(), description.getSource().getAsgName()))
              .build();

      DescribeScalableTargetsResponse result = autoScalingClient.describeScalableTargets(request);
      if (result.scalableTargets() != null && !result.scalableTargets().isEmpty()) {
        return result.scalableTargets().get(0);
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

      EcsClient ecsClient = getSourceAmazonEcsClient();

      DescribeServicesRequest request =
          DescribeServicesRequest.builder()
              .cluster(description.getEcsClusterName())
              .services(description.getSource().getAsgName())
              .build();

      DescribeServicesResponse result = ecsClient.describeServices(request);
      if (result.services() != null && !result.services().isEmpty()) {
        return result.services().get(0);
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

  private GetRoleResponse checkRoleTrustRelations(String roleName) {
    updateTaskStatus("Checking role trust relations for: " + roleName);
    IamClient iamClient = getAmazonIdentityManagementClient();

    GetRoleResponse response =
        iamClient.getRole(GetRoleRequest.builder().roleName(roleName).build());
    Role role = response.role();

    Set<IamTrustRelationship> trustedEntities =
        iamPolicyReader.getTrustedEntities(role.assumeRolePolicyDocument());

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
    namesByRegion.put(getRegion(), service.serviceName());

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

      String containerToUse =
          StringUtils.isNotBlank(targetGroupAssociation.getContainerName())
              ? targetGroupAssociation.getContainerName()
              : containerName;

      ElasticLoadBalancingV2Client loadBalancingV2 = getAmazonElasticLoadBalancingClient();

      DescribeTargetGroupsRequest request =
          DescribeTargetGroupsRequest.builder()
              .names(targetGroupAssociation.getTargetGroup())
              .build();
      DescribeTargetGroupsResponse describeTargetGroupsResult =
          loadBalancingV2.describeTargetGroups(request);

      String targetGroupArn;
      if (describeTargetGroupsResult.targetGroups().size() == 1) {
        targetGroupArn = describeTargetGroupsResult.targetGroups().get(0).targetGroupArn();
      } else if (describeTargetGroupsResult.targetGroups().size() > 1) {
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

      LoadBalancer loadBalancer =
          LoadBalancer.builder()
              .containerName(containerToUse)
              .containerPort(targetGroupAssociation.getContainerPort())
              .targetGroupArn(targetGroupArn)
              .build();

      loadBalancers.add(loadBalancer);
    }

    return loadBalancers;
  }

  private ApplicationAutoScalingClient getSourceAmazonApplicationAutoScalingClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials =
        credentialsRepository.getOne(description.getSource().getAccount());
    return amazonClientProvider.getAmazonApplicationAutoScalingV2(sourceCredentials, sourceRegion);
  }

  private EcsClient getSourceAmazonEcsClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials =
        credentialsRepository.getOne(description.getSource().getAccount());
    return amazonClientProvider.getAmazonEcsV2(sourceCredentials, sourceRegion);
  }

  private ElasticLoadBalancingV2Client getAmazonElasticLoadBalancingClient() {
    NetflixAmazonCredentials credentialAccount = description.getCredentials();
    return amazonClientProvider.getAmazonElasticLoadBalancingV2V2(credentialAccount, getRegion());
  }

  private IamClient getAmazonIdentityManagementClient() {
    NetflixAmazonCredentials credentialAccount = description.getCredentials();
    return amazonClientProvider.getIamV2(credentialAccount, getRegion());
  }

  private String getServerGroupName(Service service) {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + service.serviceName();
  }

  private Collection<KeyValuePair> setSpinnakerEnvVars(
      Collection<KeyValuePair> targetEnv, EcsServerGroupName newServerGroupName) {

    Moniker moniker = newServerGroupName.getMoniker();

    targetEnv.add(
        KeyValuePair.builder()
            .name("SERVER_GROUP")
            .value(newServerGroupName.getServiceName())
            .build());
    targetEnv.add(KeyValuePair.builder().name("CLOUD_STACK").value(moniker.getStack()).build());
    targetEnv.add(KeyValuePair.builder().name("CLOUD_DETAIL").value(moniker.getDetail()).build());

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
