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
import com.amazonaws.services.applicationautoscaling.model.*;
import com.amazonaws.services.ecs.AmazonECS;
import com.amazonaws.services.ecs.model.*;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeTargetGroupsResult;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.model.GetRoleRequest;
import com.amazonaws.services.identitymanagement.model.GetRoleResult;
import com.amazonaws.services.identitymanagement.model.Role;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.AssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAssumeRoleAmazonCredentials;
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult;
import com.netflix.spinnaker.clouddriver.ecs.deploy.EcsServerGroupNameResolver;
import com.netflix.spinnaker.clouddriver.ecs.deploy.description.CreateServerGroupDescription;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamPolicyReader;
import com.netflix.spinnaker.clouddriver.ecs.provider.agent.IamTrustRelationship;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixAssumeRoleEcsCredentials;
import com.netflix.spinnaker.clouddriver.ecs.services.EcsCloudMetricService;
import com.netflix.spinnaker.clouddriver.ecs.services.SecurityGroupSelector;
import com.netflix.spinnaker.clouddriver.ecs.services.SubnetSelector;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

public class CreateServerGroupAtomicOperation extends AbstractEcsAtomicOperation<CreateServerGroupDescription, DeploymentResult> {

  private static final String NECESSARY_TRUSTED_SERVICE = "ecs-tasks.amazonaws.com";
  protected static final String AWSVPC_NETWORK_MODE = "awsvpc";
  protected static final String FARGATE_LAUNCH_TYPE = "FARGATE";
  protected static final String NO_IAM_ROLE = "None (No IAM role)";
  protected static final String NO_IMAGE_CREDENTIALS = "None (No registry credentials)";

  protected static final String DOCKER_LABEL_KEY_SERVERGROUP = "spinnaker.servergroup";
  protected static final String DOCKER_LABEL_KEY_STACK = "spinnaker.stack";
  protected static final String DOCKER_LABEL_KEY_DETAIL = "spinnaker.detail";

  @Autowired
  EcsCloudMetricService ecsCloudMetricService;
  @Autowired
  IamPolicyReader iamPolicyReader;

  @Autowired
  SubnetSelector subnetSelector;

  @Autowired
  SecurityGroupSelector securityGroupSelector;

  public CreateServerGroupAtomicOperation(CreateServerGroupDescription description) {
    super(description, "CREATE_ECS_SERVER_GROUP");
  }

  @Override
  public DeploymentResult operate(List priorOutputs) {
    updateTaskStatus("Initializing Create Amazon ECS Server Group Operation...");

    AmazonCredentials credentials = getCredentials();

    AmazonECS ecs = getAmazonEcsClient();

    EcsServerGroupNameResolver serverGroupNameResolver = new EcsServerGroupNameResolver(description.getEcsClusterName(),
      ecs, getRegion());
    String newServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.getApplication(),
      description.getStack(), description.getFreeFormDetails(), false);

    ScalableTarget sourceTarget = getSourceScalableTarget();
    Service sourceService = getSourceService();

    String ecsServiceRole = inferAssumedRoleArn(credentials);

    updateTaskStatus("Creating Amazon ECS Task Definition...");
    TaskDefinition taskDefinition = registerTaskDefinition(ecs, ecsServiceRole, newServerGroupName);
    updateTaskStatus("Done creating Amazon ECS Task Definition...");

    Service service = createService(ecs, taskDefinition, ecsServiceRole, newServerGroupName, sourceService);

    String resourceId = registerAutoScalingGroup(credentials, service, sourceTarget);

    if (description.isCopySourceScalingPoliciesAndActions() && sourceTarget != null) {
      updateTaskStatus("Copying scaling policies...");
      ecsCloudMetricService.copyScalingPolicies(
        description.getCredentialAccount(),
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

  protected TaskDefinition registerTaskDefinition(AmazonECS ecs, String ecsServiceRole, String newServerGroupName) {
    RegisterTaskDefinitionRequest request = makeTaskDefinitionRequest(ecsServiceRole, newServerGroupName);

    RegisterTaskDefinitionResult registerTaskDefinitionResult = ecs.registerTaskDefinition(request);

    return registerTaskDefinitionResult.getTaskDefinition();
  }

  protected RegisterTaskDefinitionRequest makeTaskDefinitionRequest(String ecsServiceRole, String newServerGroupName) {
    Collection<KeyValuePair> containerEnvironment = new LinkedList<>();

    // Set all user defined environment variables
    final Map<String, String> environmentVariables = description.getEnvironmentVariables();
    if(environmentVariables != null) {
      for (Map.Entry<String, String> entry : environmentVariables.entrySet()) {
        containerEnvironment.add(new KeyValuePair().withName(entry.getKey()).withValue(entry.getValue()));
      }
    }

    containerEnvironment.add(new KeyValuePair().withName("SERVER_GROUP").withValue(newServerGroupName));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_STACK").withValue(description.getStack()));
    containerEnvironment.add(new KeyValuePair().withName("CLOUD_DETAIL").withValue(description.getFreeFormDetails()));

    PortMapping portMapping = new PortMapping()
      .withProtocol(description.getPortProtocol() != null ? description.getPortProtocol() : "tcp");

    if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
      portMapping
        .withHostPort(description.getContainerPort())
        .withContainerPort(description.getContainerPort());
    } else {
      portMapping
        .withHostPort(0)
        .withContainerPort(description.getContainerPort());
    }

    Collection<PortMapping> portMappings = new LinkedList<>();
    portMappings.add(portMapping);

    ContainerDefinition containerDefinition = new ContainerDefinition()
      .withName(EcsServerGroupNameResolver.getEcsContainerName(newServerGroupName))
      .withEnvironment(containerEnvironment)
      .withPortMappings(portMappings)
      .withCpu(description.getComputeUnits())
      .withMemoryReservation(description.getReservedMemory())
      .withImage(description.getDockerImageAddress());

    if (!NO_IMAGE_CREDENTIALS.equals(description.getDockerImageCredentialsSecret()) &&
      description.getDockerImageCredentialsSecret() != null) {
      RepositoryCredentials credentials = new RepositoryCredentials()
        .withCredentialsParameter(description.getDockerImageCredentialsSecret());
      containerDefinition.withRepositoryCredentials(credentials);
    }

    Map<String, String> labelsMap = new HashMap<>();
    if (description.getDockerLabels() != null) {
      labelsMap.putAll(description.getDockerLabels());
    }

    if (description.getStack() != null) {
      labelsMap.put(DOCKER_LABEL_KEY_STACK, description.getStack());
    }

    if (description.getFreeFormDetails() != null) {
      labelsMap.put(DOCKER_LABEL_KEY_DETAIL, description.getFreeFormDetails());
    }

    labelsMap.put(DOCKER_LABEL_KEY_SERVERGROUP, newServerGroupName);

    containerDefinition.withDockerLabels(labelsMap);

    if (description.getLogDriver() != null && !"None".equals(description.getLogDriver())) {
      LogConfiguration logConfiguration = new LogConfiguration()
        .withLogDriver(description.getLogDriver())
        .withOptions(description.getLogOptions());

      containerDefinition.withLogConfiguration(logConfiguration);
    }

    Collection<ContainerDefinition> containerDefinitions = new LinkedList<>();
    containerDefinitions.add(containerDefinition);

    RegisterTaskDefinitionRequest request = new RegisterTaskDefinitionRequest()
      .withContainerDefinitions(containerDefinitions)
      .withFamily(EcsServerGroupNameResolver.getEcsFamilyName(newServerGroupName));
    if (description.getNetworkMode() != null && !description.getNetworkMode().equals("default")) {
      request.withNetworkMode(description.getNetworkMode());
    }

    if (!NO_IAM_ROLE.equals(description.getIamRole()) && description.getIamRole() != null) {
      checkRoleTrustRelations(description.getIamRole());
      request.setTaskRoleArn(description.getIamRole());
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      request.setRequiresCompatibilities(Arrays.asList(description.getLaunchType()));
    }

    if (FARGATE_LAUNCH_TYPE.equals(description.getLaunchType())) {
      request.setExecutionRoleArn(ecsServiceRole);
      request.setCpu(description.getComputeUnits().toString());
      request.setMemory(description.getReservedMemory().toString());
    }

    return request;
  }

  private Service createService(AmazonECS ecs, TaskDefinition taskDefinition, String ecsServiceRole,
                                String newServerGroupName, Service sourceService) {
    Collection<LoadBalancer> loadBalancers = new LinkedList<>();
    loadBalancers.add(retrieveLoadBalancer(EcsServerGroupNameResolver.getEcsContainerName(newServerGroupName)));

    Integer desiredCount = description.getCapacity().getDesired();
    if (sourceService != null &&
      description.getSource() != null &&
      description.getSource().getUseSourceCapacity() != null &&
      description.getSource().getUseSourceCapacity()) {
      desiredCount = sourceService.getDesiredCount();
    }

    String taskDefinitionArn = taskDefinition.getTaskDefinitionArn();

    DeploymentConfiguration deploymentConfiguration = new DeploymentConfiguration()
      .withMinimumHealthyPercent(100)
      .withMaximumPercent(200);

    CreateServiceRequest request = new CreateServiceRequest()
      .withServiceName(newServerGroupName)
      .withDesiredCount(desiredCount)
      .withCluster(description.getEcsClusterName())
      .withLoadBalancers(loadBalancers)
      .withTaskDefinition(taskDefinitionArn)
      .withPlacementStrategy(description.getPlacementStrategySequence())
      .withDeploymentConfiguration(deploymentConfiguration);

    if (!AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
      request.withRole(ecsServiceRole);
    }

    if (AWSVPC_NETWORK_MODE.equals(description.getNetworkMode())) {
      Collection<String> subnetIds = subnetSelector.resolveSubnetsIds(description.getAccount(), description.getRegion(), description.getSubnetType());
      Collection<String> vpcIds = subnetSelector.getSubnetVpcIds(description.getAccount(), description.getRegion(), subnetIds);
      Collection<String> securityGroupIds = securityGroupSelector.resolveSecurityGroupNames(
        description.getAccount(),
        description.getRegion(),
        description.getSecurityGroupNames(),
        vpcIds);

      AwsVpcConfiguration awsvpcConfiguration = new AwsVpcConfiguration()
        .withSecurityGroups(securityGroupIds)
        .withSubnets(subnetIds);

      if (description.getAssociatePublicIpAddress() != null) {
        awsvpcConfiguration.withAssignPublicIp(description.getAssociatePublicIpAddress() ? "ENABLED" : "DISABLED");
      }

      request.withNetworkConfiguration(new NetworkConfiguration().withAwsvpcConfiguration(awsvpcConfiguration));
    }

    if (!StringUtils.isEmpty(description.getLaunchType())) {
      request.withLaunchType(description.getLaunchType());
    }

    if (description.getHealthCheckGracePeriodSeconds() != null) {
      request.withHealthCheckGracePeriodSeconds(description.getHealthCheckGracePeriodSeconds());
    }

    updateTaskStatus(String.format("Creating %s of %s with %s for %s.",
      desiredCount, newServerGroupName, taskDefinitionArn, description.getCredentialAccount()));

    Service service = ecs.createService(request).getService();

    updateTaskStatus(String.format("Done creating %s of %s with %s for %s.",
      desiredCount, newServerGroupName, taskDefinitionArn, description.getCredentialAccount()));

    return service;
  }

  private String registerAutoScalingGroup(AmazonCredentials credentials,
                                          Service service,
                                          ScalableTarget sourceTarget) {

    AWSApplicationAutoScaling autoScalingClient = getAmazonApplicationAutoScalingClient();
    String assumedRoleArn = inferAssumedRoleArn(credentials);

    Integer min = description.getCapacity().getMin();
    Integer max = description.getCapacity().getMax();

    if (sourceTarget != null &&
      description.getSource() != null &&
      description.getSource().getUseSourceCapacity() != null &&
      description.getSource().getUseSourceCapacity()) {
      min = sourceTarget.getMinCapacity();
      max = sourceTarget.getMaxCapacity();
    }

    RegisterScalableTargetRequest request = new RegisterScalableTargetRequest()
      .withServiceNamespace(ServiceNamespace.Ecs)
      .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
      .withResourceId(String.format("service/%s/%s", description.getEcsClusterName(), service.getServiceName()))
      .withRoleARN(assumedRoleArn)
      .withMinCapacity(min)
      .withMaxCapacity(max);

    updateTaskStatus("Creating Amazon Application Auto Scaling Scalable Target Definition...");
    autoScalingClient.registerScalableTarget(request);
    updateTaskStatus("Done creating Amazon Application Auto Scaling Scalable Target Definition.");

    return request.getResourceId();
  }

  private ScalableTarget getSourceScalableTarget() {
    if (description.getSource() != null
      && description.getSource().getRegion() != null
      && description.getSource().getAccount() != null
      && description.getSource().getAsgName() != null) {

      AWSApplicationAutoScaling autoScalingClient = getSourceAmazonApplicationAutoScalingClient();

      DescribeScalableTargetsRequest request = new DescribeScalableTargetsRequest()
        .withServiceNamespace(ServiceNamespace.Ecs)
        .withScalableDimension(ScalableDimension.EcsServiceDesiredCount)
        .withResourceIds(String.format("service/%s/%s", description.getEcsClusterName(), description.getSource().getAsgName()));

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

      DescribeServicesRequest request = new DescribeServicesRequest()
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
      throw new UnsupportedOperationException("The given kind of credentials is not supported, " +
        "please report this issue to the Spinnaker project on Github.");
    }

    return String.format("arn:aws:iam::%s:%s", credentials.getAccountId(), role);
  }

  private void checkRoleTrustRelations(String roleName) {
    updateTaskStatus("Checking role trust relations for: " + roleName);
    AmazonIdentityManagement iamClient = getAmazonIdentityManagementClient();

    GetRoleResult response = iamClient.getRole(new GetRoleRequest()
      .withRoleName(roleName));
    Role role = response.getRole();

    Set<IamTrustRelationship> trustedEntities = iamPolicyReader.getTrustedEntities(role.getAssumeRolePolicyDocument());

    Set<String> trustedServices = trustedEntities.stream()
      .filter(trustRelation -> trustRelation.getType().equals("Service"))
      .map(IamTrustRelationship::getValue)
      .collect(Collectors.toSet());

    if (!trustedServices.contains(NECESSARY_TRUSTED_SERVICE)) {
      throw new IllegalArgumentException("The " + roleName + " role does not have a trust relationship to ecs-tasks.amazonaws.com.");
    }
  }

  private DeploymentResult makeDeploymentResult(Service service) {
    Map<String, String> namesByRegion = new HashMap<>();
    namesByRegion.put(getRegion(), service.getServiceName());

    DeploymentResult result = new DeploymentResult();
    result.setServerGroupNames(Arrays.asList(getServerGroupName(service)));
    result.setServerGroupNameByRegion(namesByRegion);
    return result;
  }

  private LoadBalancer retrieveLoadBalancer(String containerName) {
    LoadBalancer loadBalancer = new LoadBalancer();
    loadBalancer.setContainerName(containerName);
    loadBalancer.setContainerPort(description.getContainerPort());

    if (description.getTargetGroup() != null) {
      AmazonElasticLoadBalancing loadBalancingV2 = getAmazonElasticLoadBalancingClient();

      DescribeTargetGroupsRequest request = new DescribeTargetGroupsRequest().withNames(description.getTargetGroup());
      DescribeTargetGroupsResult describeTargetGroupsResult = loadBalancingV2.describeTargetGroups(request);

      if (describeTargetGroupsResult.getTargetGroups().size() == 1) {
        loadBalancer.setTargetGroupArn(describeTargetGroupsResult.getTargetGroups().get(0).getTargetGroupArn());
      } else if (describeTargetGroupsResult.getTargetGroups().size() > 1) {
        throw new IllegalArgumentException("There are multiple target groups with the name " + description.getTargetGroup() + ".");
      } else {
        throw new IllegalArgumentException("There is no target group with the name " + description.getTargetGroup() + ".");
      }

    }
    return loadBalancer;
  }

  private AWSApplicationAutoScaling getSourceAmazonApplicationAutoScalingClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials = (NetflixAmazonCredentials)accountCredentialsProvider.getCredentials(description.getSource().getAccount());
    return amazonClientProvider.getAmazonApplicationAutoScaling(sourceCredentials, sourceRegion, false);
  }

  private AmazonECS getSourceAmazonEcsClient() {
    String sourceRegion = description.getSource().getRegion();
    NetflixAmazonCredentials sourceCredentials = (NetflixAmazonCredentials)accountCredentialsProvider.getCredentials(description.getSource().getAccount());
    return amazonClientProvider.getAmazonEcs(sourceCredentials, sourceRegion, false);
  }

  private AWSApplicationAutoScaling getAmazonApplicationAutoScalingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonApplicationAutoScaling(credentialAccount, getRegion(), false);
  }

  private AmazonElasticLoadBalancing getAmazonElasticLoadBalancingClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonElasticLoadBalancingV2(credentialAccount, getRegion(), false);
  }

  private AmazonIdentityManagement getAmazonIdentityManagementClient() {
    AWSCredentialsProvider credentialsProvider = getCredentials().getCredentialsProvider();
    NetflixAmazonCredentials credentialAccount = description.getCredentials();

    return amazonClientProvider.getAmazonIdentityManagement(credentialAccount, getRegion(), false);
  }

  private String getServerGroupName(Service service) {
    // See in Orca MonitorKatoTask#getServerGroupNames for a reason for this
    return getRegion() + ":" + service.getServiceName();
  }

  @Override
  protected String getRegion() {
    //CreateServerGroupDescription does not contain a region. Instead it has AvailabilityZones
    return description.getAvailabilityZones().keySet().iterator().next();
  }
}
