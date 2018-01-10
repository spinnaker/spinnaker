/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.clouddriver.titus.deploy.handlers

import com.netflix.spinnaker.clouddriver.aws.AwsConfiguration
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper.TargetGroupLookupResult
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeployDescription
import com.netflix.spinnaker.clouddriver.deploy.DeployHandler
import com.netflix.spinnaker.clouddriver.helpers.OperationPoller
import com.netflix.spinnaker.clouddriver.orchestration.events.CreateServerGroupEvent
import com.netflix.spinnaker.clouddriver.security.AccountCredentials
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import com.netflix.spinnaker.clouddriver.titus.TitusClientProvider
import com.netflix.spinnaker.clouddriver.titus.TitusCloudProvider
import com.netflix.spinnaker.clouddriver.titus.caching.utils.AwsLookupUtil
import com.netflix.spinnaker.clouddriver.titus.client.TitusAutoscalingClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusClient
import com.netflix.spinnaker.clouddriver.titus.client.TitusLoadBalancerClient
import com.netflix.spinnaker.clouddriver.titus.client.model.Job
import com.netflix.spinnaker.clouddriver.titus.client.model.SubmitJobRequest
import com.netflix.spinnaker.clouddriver.titus.credentials.NetflixTitusCredentials
import com.netflix.spinnaker.clouddriver.titus.deploy.TitusServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription
import com.netflix.spinnaker.clouddriver.titus.deploy.description.TitusDeployDescription.Source
import com.netflix.spinnaker.clouddriver.titus.deploy.description.UpsertTitusScalingPolicyDescription
import com.netflix.spinnaker.clouddriver.titus.model.DockerImage
import com.netflix.titus.grpc.protogen.PutPolicyRequest
import com.netflix.titus.grpc.protogen.PutPolicyRequest.Builder
import com.netflix.titus.grpc.protogen.ScalingPolicyResult
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus.ScalingPolicyState
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {

  @Autowired
  AwsLookupUtil awsLookupUtil

  @Autowired
  AwsConfiguration.DeployDefaults deployDefaults

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  private final Logger logger = LoggerFactory.getLogger(TitusDeployHandler)

  private static final String BASE_PHASE = "DEPLOY"

  private final TitusClientProvider titusClientProvider
  private final AccountCredentialsRepository accountCredentialsRepository
  private final TargetGroupLookupHelper targetGroupLookupHelper

  TitusDeployHandler(TitusClientProvider titusClientProvider, AccountCredentialsRepository accountCredentialsRepository) {
    this.titusClientProvider = titusClientProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.targetGroupLookupHelper = new TargetGroupLookupHelper()
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  TitusDeploymentResult handle(TitusDeployDescription description, List priorOutputs) {

    try {
      task.updateStatus BASE_PHASE, "Initializing handler..."
      TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
      TitusDeploymentResult deploymentResult = new TitusDeploymentResult()
      String account = description.account
      String region = description.region
      String subnet = description.subnet

      if (description.source.asgName) {
        Source source = description.source

        TitusClient sourceClient = buildSourceTitusClient(source)
        if (!sourceClient) {
          throw new RuntimeException("Unable to locate source (${source.account}:${source.region}:${source.asgName})")
        }
        Job sourceJob = sourceClient.findJobByName(source.asgName)
        if (!sourceJob) {
          throw new RuntimeException("Unable to locate source (${source.account}:${source.region}:${source.asgName})" )
        }

        task.updateStatus BASE_PHASE, "Copying deployment details from (${source.account}:${source.region}:${source.asgName})"

        description.runtimeLimitSecs = description.runtimeLimitSecs ?: sourceJob.runtimeLimitSecs
        description.securityGroups = description.securityGroups ?: sourceJob.securityGroups
        description.imageId = description.imageId ?: (sourceJob.applicationName + ":" + sourceJob.version)

        if (description.source.useSourceCapacity) {
          description.capacity.min = sourceJob.instancesMin
          description.capacity.max = sourceJob.instancesMax
          description.capacity.desired = sourceJob.instancesDesired
        }

        description.resources.cpu = description.resources.cpu ?: sourceJob.cpu
        description.resources.memory = description.resources.memory ?: sourceJob.memory
        description.resources.disk = description.resources.disk ?: sourceJob.disk
        description.retries = description.retries ?: sourceJob.retries
        description.runtimeLimitSecs = description.runtimeLimitSecs ?: sourceJob.runtimeLimitSecs
        description.resources.gpu = description.resources.gpu ?: sourceJob.gpu
        description.resources.networkMbps = description.resources.networkMbps ?: sourceJob.networkMbps
        description.efs = description.efs ?: sourceJob.efs
        description.env = description.env != null ? description.env : sourceJob.environment
        description.resources.allocateIpAddress = description.resources.allocateIpAddress ?: sourceJob.allocateIpAddress
        description.entryPoint = description.entryPoint ?: sourceJob.entryPoint
        description.iamProfile = description.iamProfile ?: sourceJob.iamProfile
        description.capacityGroup = description.capacityGroup ?: sourceJob.capacityGroup

        if(!description.labels || description.labels.isEmpty()){
          if(!description.labels){
            description.labels = [:]
          }
          sourceJob.labels.each{ k, v -> description.labels.put(k, v)}
        }
        description.inService = description.inService ?: sourceJob.inService
        description.migrationPolicy = description.migrationPolicy ?: sourceJob.migrationPolicy
        description.jobType = description.jobType ?: "service"
        if (!description.hardConstraints) description.hardConstraints = []
        if (!description.softConstraints) description.softConstraints = []
        if (description.softConstraints.empty && sourceJob.softConstraints) {
          sourceJob.softConstraints.each {
            if (!description.hardConstraints.contains(it)) {
              description.softConstraints.add(it)
            }
          }
        }
        if (description.hardConstraints.empty && sourceJob.hardConstraints) {
          sourceJob.hardConstraints.each {
            if (!description.softConstraints.contains(it)) {
              description.hardConstraints.add(it)
            }
          }
        }
      }

      task.updateStatus BASE_PHASE, "Preparing deployment to ${account}:${region}${subnet ? ':' + subnet : ''}..."
      DockerImage dockerImage = new DockerImage(description.imageId)

      TitusServerGroupNameResolver serverGroupNameResolver = new TitusServerGroupNameResolver(titusClient, description.region)
      String nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
      task.updateStatus BASE_PHASE, "Resolved server group name to ${nextServerGroupName}"

      if (!description.env) description.env = [:]
      if (!description.labels) description.labels = [:]

      if (description.interestingHealthProviderNames && !description.interestingHealthProviderNames.empty) {
        description.labels.put("interestingHealthProviderNames", description.interestingHealthProviderNames.join(","))
      }

      SubmitJobRequest submitJobRequest = new SubmitJobRequest()
        .withJobName(nextServerGroupName)
        .withApplication(description.application)
        .withDockerImageName(dockerImage.imageName)
        .withDockerImageVersion(dockerImage.imageVersion)
        .withInstancesMin(description.capacity.min)
        .withInstancesMax(description.capacity.max)
        .withInstancesDesired(description.capacity.desired)
        .withCpu(description.resources.cpu)
        .withMemory(description.resources.memory)
        .withDisk(description.resources.disk)
        .withRetries(description.retries)
        .withRuntimeLimitSecs(description.runtimeLimitSecs)
        .withGpu(description.resources.gpu)
        .withNetworkMbps(description.resources.networkMbps)
        .withEfs(description.efs)
        .withPorts(description.resources.ports)
        .withEnv(description.env)
        .withAllocateIpAddress(description.resources.allocateIpAddress)
        .withStack(description.stack)
        .withDetail(description.freeFormDetails)
        .withEntryPoint(description.entryPoint)
        .withIamProfile(description.iamProfile)
        .withCapacityGroup(description.capacityGroup)
        .withLabels(description.labels)
        .withInService(description.inService)
        .withMigrationPolicy(description.migrationPolicy)
        .withCredentials(description.credentials.name)

      Set<String> securityGroups = []
      description.securityGroups?.each { providedSecurityGroup ->
        if (awsLookupUtil.securityGroupIdExists(account, region, providedSecurityGroup)) {
          securityGroups << providedSecurityGroup
        } else {
          String convertedSecurityGroup = awsLookupUtil.convertSecurityGroupNameToId(account, region, providedSecurityGroup)
          if (!convertedSecurityGroup) {
            throw new RuntimeException("Security Group ${providedSecurityGroup} cannot be found")
          }
          securityGroups << convertedSecurityGroup
        }
      }

      if (description.jobType != 'batch' && deployDefaults.addAppGroupToServerGroup && securityGroups.size() < deployDefaults.maxSecurityGroups && description.useApplicationDefaultSecurityGroup != false) {
        String applicationSecurityGroup = awsLookupUtil.convertSecurityGroupNameToId(account, region, description.application)
        if (!applicationSecurityGroup) {
          applicationSecurityGroup = OperationPoller.retryWithBackoff({ o -> awsLookupUtil.createSecurityGroupForApplication(account, region, description.application) }, 1000, 5)
        }
        if (!securityGroups.contains(applicationSecurityGroup)) {
          securityGroups << applicationSecurityGroup
        }
      }

      if (description.hardConstraints) {
        description.hardConstraints.each { constraint ->
          submitJobRequest.withConstraint(SubmitJobRequest.Constraint.hard(constraint))
        }
      }

      if (description.softConstraints) {
        description.softConstraints.each { constraint ->
          submitJobRequest.withConstraint(SubmitJobRequest.Constraint.soft(constraint))
        }
      }

      if (!description.hardConstraints?.contains(SubmitJobRequest.Constraint.ZONE_BALANCE) && !description.softConstraints?.contains(SubmitJobRequest.Constraint.ZONE_BALANCE)) {
        submitJobRequest.withConstraint(SubmitJobRequest.Constraint.soft(SubmitJobRequest.Constraint.ZONE_BALANCE))
      }

      if (!securityGroups.empty) {
        submitJobRequest.withSecurityGroups(securityGroups.asList())
      }

      if (description.user) {
        submitJobRequest.withUser(description.user)
      }

      if (description.jobType) {
        submitJobRequest.withJobType(description.jobType)
      }

      TargetGroupLookupResult targetGroupLookupResult

      if (description.targetGroups) {
        targetGroupLookupResult = validateLoadBalancers(description)
      }

      task.updateStatus BASE_PHASE, "Submitting job request to Titus..."
      String jobUri = titusClient.submitJob(submitJobRequest)

      task.updateStatus BASE_PHASE, "Successfully submitted job request to Titus (Job URI: ${jobUri})"

      deploymentResult.serverGroupNames = ["${region}:${nextServerGroupName}".toString()]
      deploymentResult.serverGroupNameByRegion = [(description.region): nextServerGroupName]
      deploymentResult.jobUri = jobUri

      if (description.jobType == 'batch') {
        deploymentResult = new TitusDeploymentResult([
          deployedNames          : [jobUri],
          deployedNamesByLocation: [(description.region): [jobUri]],
          jobUri                 : jobUri
        ])
      }

      copyScalingPolicies(description, jobUri, nextServerGroupName)

      addLoadBalancers(description, targetGroupLookupResult, jobUri)

      deploymentResult.messages = task.history.collect { "${it.phase} : ${it.status}".toString() }

      description.events << new CreateServerGroupEvent(
        TitusCloudProvider.ID, getAccountId(account), region, nextServerGroupName
      )

      return deploymentResult
    } catch (t) {
      task.updateStatus(BASE_PHASE, "Task failed $t.message")
      task.fail()
      logger.error("Deploy failed", t)
      throw t
    }
  }

  protected TargetGroupLookupResult validateLoadBalancers(TitusDeployDescription description) {
    if (!description.targetGroups) {
      return null
    }
    def regionScopedProvider = regionScopedProviderFactory.forRegion(accountCredentialsProvider.getCredentials(description.credentials.awsAccount), description.region)
    def targetGroups = targetGroupLookupHelper.getTargetGroupsByName(regionScopedProvider, description.targetGroups)
    if (targetGroups.unknownTargetGroups) {
      throw new IllegalStateException("Unable to find target groups named $targetGroups.unknownTargetGroups")
    }
    return targetGroups
  }

  protected void addLoadBalancers(TitusDeployDescription description, TargetGroupLookupResult targetGroups, String jobUri) {
    TitusLoadBalancerClient loadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(description.credentials, description.region)
    if (!loadBalancerClient) {
      task.updateStatus BASE_PHASE, "Unable to create load balancing client in target account/region"
      return
    }
    targetGroups.targetGroupARNs.each { targetGroupARN ->
      loadBalancerClient.addLoadBalancer(jobUri, targetGroupARN)
      task.updateStatus BASE_PHASE, "Attached ${targetGroupARN} to ${jobUri}"
    }
  }

  protected void copyScalingPolicies(TitusDeployDescription description, String jobUri) {
    if (!description.copySourceScalingPolicies) {
      return
    }
    Source source = description.source
    TitusClient sourceClient = buildSourceTitusClient(source)
    TitusAutoscalingClient autoscalingClient = titusClientProvider.getTitusAutoscalingClient(description.credentials, description.region)
    if (!autoscalingClient) {
      task.updateStatus BASE_PHASE, "Unable to create client in target account/region; policies will not be copied"
      return
    }
    TitusAutoscalingClient sourceAutoscalingClient = buildSourceAutoscalingClient(source)
    if (!sourceClient) {
      task.updateStatus BASE_PHASE, "Unable to create client in source account/region; policies will not be copied"
      return
    }
    if (sourceClient && sourceAutoscalingClient) {
      Job sourceJob = sourceClient.findJobByName(source.asgName)
      if (!sourceJob) {
        task.updateStatus BASE_PHASE, "Unable to locate source (${source.account}:${source.region}:${source.asgName})"
      } else {
        task.updateStatus BASE_PHASE, "Copying scaling policies from source (Job URI: ${sourceJob.id})"
        List<ScalingPolicyResult> policies = sourceAutoscalingClient.getJobScalingPolicies(sourceJob.id) ?: []
        task.updateStatus BASE_PHASE, "Found ${policies.size()} scaling policies for source (Job URI: ${jobUri})"
        policies.each { policy ->
          // Don't copy deleting or deleted policies
          if (![ScalingPolicyState.Deleted, ScalingPolicyState.Deleting].contains(policy.policyState.state)) {
            Builder requestBuilder = PutPolicyRequest.newBuilder()
              .setJobId(jobUri)
              .setScalingPolicy(UpsertTitusScalingPolicyDescription.fromScalingPolicyResult(description.region, policy).toScalingPolicyBuilder())
            autoscalingClient.upsertScalingPolicy(requestBuilder.build())
          }
        }
      }
    }
    task.updateStatus BASE_PHASE, "Copy scaling policies succeeded (Job URI: ${jobUri})"
  }

  private TitusClient buildSourceTitusClient(Source source) {
    if (source.account && source.region && source.asgName) {
      def sourceRegion = source.region
      def sourceCredentials = accountCredentialsRepository.getOne(source.account) as NetflixTitusCredentials
      return titusClientProvider.getTitusClient(sourceCredentials, sourceRegion)
    }

    return null
  }

  private TitusAutoscalingClient buildSourceAutoscalingClient(Source source) {
    if (source.account && source.region && source.asgName) {
      def sourceRegion = source.region
      def sourceCredentials = accountCredentialsRepository.getOne(source.account) as NetflixTitusCredentials
      return titusClientProvider.getTitusAutoscalingClient(sourceCredentials, sourceRegion)
    }

    return null
  }

  private String getAccountId(String credentials) {
    AccountCredentials accountCredentials = accountCredentialsProvider.getCredentials(credentials)
    if (accountCredentials instanceof NetflixTitusCredentials) {
      return accountCredentialsProvider.getCredentials(accountCredentials.awsAccount).accountId
    }

    return accountCredentials.accountId
  }

  @Override
  boolean handles(DeployDescription description) {
    return description instanceof TitusDeployDescription
  }
}
