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

import com.netflix.frigga.Names
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper
import com.netflix.spinnaker.clouddriver.aws.deploy.ops.loadbalancer.TargetGroupLookupHelper.TargetGroupLookupResult
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
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
import com.netflix.spinnaker.clouddriver.titus.TitusException
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
import com.netflix.spinnaker.config.AwsConfiguration
import com.netflix.spinnaker.kork.core.RetrySupport
import com.netflix.titus.grpc.protogen.PutPolicyRequest
import com.netflix.titus.grpc.protogen.PutPolicyRequest.Builder
import com.netflix.titus.grpc.protogen.ScalingPolicyResult
import com.netflix.titus.grpc.protogen.ScalingPolicyStatus.ScalingPolicyState
import groovy.util.logging.Slf4j
import io.grpc.Status
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Slf4j
class TitusDeployHandler implements DeployHandler<TitusDeployDescription> {

  public static final String USE_APPLICATION_DEFAULT_SG_LABEL = 'spinnaker.useApplicationDefaultSecurityGroup'

  @Autowired
  AwsLookupUtil awsLookupUtil

  @Autowired
  AwsConfiguration.DeployDefaults deployDefaults

  @Autowired
  AccountCredentialsProvider accountCredentialsProvider

  @Autowired
  RegionScopedProviderFactory regionScopedProviderFactory

  @Autowired
  Front50Service front50Service

  private final Logger logger = LoggerFactory.getLogger(TitusDeployHandler)

  private static final String BASE_PHASE = "DEPLOY"

  private final TitusClientProvider titusClientProvider
  private final AccountCredentialsRepository accountCredentialsRepository
  private final TargetGroupLookupHelper targetGroupLookupHelper
  private final RetrySupport retrySupport

  TitusDeployHandler(TitusClientProvider titusClientProvider, AccountCredentialsRepository accountCredentialsRepository) {
    this.titusClientProvider = titusClientProvider
    this.accountCredentialsRepository = accountCredentialsRepository
    this.targetGroupLookupHelper = new TargetGroupLookupHelper()
    this.retrySupport = new RetrySupport()
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  TitusDeploymentResult handle(TitusDeployDescription description, List priorOutputs) {

    try {
      task.updateStatus BASE_PHASE, "Initializing handler... ${System.currentTimeMillis()}"
      TitusClient titusClient = titusClientProvider.getTitusClient(description.credentials, description.region)
      TitusDeploymentResult deploymentResult = new TitusDeploymentResult()
      String account = description.account
      String region = description.region
      String subnet = description.subnet

      if (!description.env) description.env = [:]
      if (!description.containerAttributes) description.containerAttributes = [:]
      if (!description.labels) description.labels = [:]

      if (description.source.asgName) {
        task.updateStatus BASE_PHASE, "Getting Source ASG Name Details... ${System.currentTimeMillis()}"


        // If cluster name info was not provided, use the fields from the source asg
        def sourceName = Names.parseName(description.source.asgName)
        description.application = description.application != null ? description.application : sourceName.app
        description.stack = description.stack != null ? description.stack : sourceName.stack
        description.freeFormDetails = description.freeFormDetails != null ? description.freeFormDetails : sourceName.detail

        Source source = description.source

        TitusClient sourceClient = buildSourceTitusClient(source)
        if (!sourceClient) {
          throw new RuntimeException("Unable to locate source (${source.account}:${source.region}:${source.asgName})")
        }
        Job sourceJob = sourceClient.findJobByName(source.asgName)
        if (!sourceJob) {
          throw new RuntimeException("Unable to locate source (${source.account}:${source.region}:${source.asgName})")
        }

        task.updateStatus BASE_PHASE, "Copying deployment details from (${source.account}:${source.region}:${source.asgName})"

        description.runtimeLimitSecs = description.runtimeLimitSecs ?: sourceJob.runtimeLimitSecs
        description.securityGroups = description.securityGroups ?: sourceJob.securityGroups
        description.imageId = description.imageId ?: (sourceJob.applicationName + ":" + (sourceJob.version ?: sourceJob.digest))

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
        description.resources.allocateIpAddress = description.resources.allocateIpAddress ?: sourceJob.allocateIpAddress
        description.entryPoint = description.entryPoint ?: sourceJob.entryPoint
        description.iamProfile = description.iamProfile ?: sourceJob.iamProfile
        description.capacityGroup = description.capacityGroup ?: sourceJob.capacityGroup

        if (description.labels.isEmpty()) {
          sourceJob.labels.each { k, v -> description.labels.put(k, v) }
        }

        if (description.env.isEmpty()) {
          sourceJob.environment.each { k, v -> description.env.put(k, v) }
        }

        if (description.containerAttributes.isEmpty()) {
          sourceJob.containerAttributes.each { k, v -> description.containerAttributes.put(k, v) }
        }
        if (description.inService == null) {
          description.inService = sourceJob.inService
        }
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
        if (sourceJob.labels?.get(USE_APPLICATION_DEFAULT_SG_LABEL) == "false") {
          description.useApplicationDefaultSecurityGroup = false
        }

        task.updateStatus BASE_PHASE, "Finished Getting Source ASG Name Details... ${System.currentTimeMillis()}"

      }

      task.updateStatus BASE_PHASE, "Preparing deployment to ${account}:${region}${subnet ? ':' + subnet : ''}... ${System.currentTimeMillis()}"
      DockerImage dockerImage = new DockerImage(description.imageId)

      if (description.interestingHealthProviderNames && !description.interestingHealthProviderNames.empty) {
        description.labels.put("interestingHealthProviderNames", description.interestingHealthProviderNames.join(","))
      }

      if (description.labels.containsKey(USE_APPLICATION_DEFAULT_SG_LABEL)) {
        if (description.labels.get(USE_APPLICATION_DEFAULT_SG_LABEL) == "false") {
          description.useApplicationDefaultSecurityGroup = false
        } else {
          description.useApplicationDefaultSecurityGroup = true
        }
      }

      if (description.useApplicationDefaultSecurityGroup == false) {
        description.labels.put(USE_APPLICATION_DEFAULT_SG_LABEL, "false")
      } else {
        if (description.labels.containsKey(USE_APPLICATION_DEFAULT_SG_LABEL)) {
          description.labels.remove(USE_APPLICATION_DEFAULT_SG_LABEL)
        }
      }

      SubmitJobRequest submitJobRequest = new SubmitJobRequest()
        .withApplication(description.application)
        .withDockerImageName(dockerImage.imageName)
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
        .withContainerAttributes(description.containerAttributes.collectEntries { [(it.key): it.value?.toString()] })

      if (dockerImage.imageDigest != null) {
        submitJobRequest = submitJobRequest.withDockerDigest(dockerImage.imageDigest)
      } else {
        submitJobRequest = submitJobRequest.withDockerImageVersion(dockerImage.imageVersion)
      }

      task.updateStatus BASE_PHASE, "Resolving Security Groups... ${System.currentTimeMillis()}"

      Set<String> securityGroups = []
      description.securityGroups?.each { providedSecurityGroup ->
        task.updateStatus BASE_PHASE, "Resolving Security Group ${providedSecurityGroup}... ${System.currentTimeMillis()}"
        if (awsLookupUtil.securityGroupIdExists(account, region, providedSecurityGroup)) {
          securityGroups << providedSecurityGroup
        } else {
          task.updateStatus BASE_PHASE, "Resolving Security Group name ${providedSecurityGroup}... ${System.currentTimeMillis()}"
          String convertedSecurityGroup = awsLookupUtil.convertSecurityGroupNameToId(account, region, providedSecurityGroup)
          if (!convertedSecurityGroup) {
            throw new RuntimeException("Security Group ${providedSecurityGroup} cannot be found")
          }
          securityGroups << convertedSecurityGroup
        }
      }

      task.updateStatus BASE_PHASE, "Finished resolving Security Groups... ${System.currentTimeMillis()}"

      if (description.jobType == 'service' && deployDefaults.addAppGroupToServerGroup && securityGroups.size() < deployDefaults.maxSecurityGroups && description.useApplicationDefaultSecurityGroup != false) {
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

      if (description.getJobType() == "service" && !description.hardConstraints?.contains(SubmitJobRequest.Constraint.ZONE_BALANCE) && !description.softConstraints?.contains(SubmitJobRequest.Constraint.ZONE_BALANCE)) {
        submitJobRequest.withConstraint(SubmitJobRequest.Constraint.soft(SubmitJobRequest.Constraint.ZONE_BALANCE))
      }

      if (!securityGroups.empty) {
        submitJobRequest.withSecurityGroups(securityGroups.asList())
      }

      task.updateStatus BASE_PHASE, "Setting user email... ${System.currentTimeMillis()}"

      Map front50Application

      try {
        front50Application = front50Service.getApplication(description.getApplication())
      } catch (Exception e) {
        log.error('Failed to load front50 application attributes for {}', description.getApplication())
      }

      if (front50Application && front50Application['email']) {
        submitJobRequest.withUser(front50Application['email'])
      } else {
        if (description.user) {
          submitJobRequest.withUser(description.user)
        }
      }

      if (description.jobType) {
        submitJobRequest.withJobType(description.jobType)
      }

      task.updateStatus BASE_PHASE, "Resolving target groups... ${System.currentTimeMillis()}"

      TargetGroupLookupResult targetGroupLookupResult

      if (description.targetGroups) {
        targetGroupLookupResult = validateLoadBalancers(description)
        description.labels.put('spinnaker.targetGroups', targetGroupLookupResult?.targetGroupARNs.join(','))
      } else {
        if (description.labels.containsKey('spinnaker.targetGroups')) {
          description.labels.remove('spinnaker.targetGroups')
        }
      }

      task.updateStatus BASE_PHASE, "Resolving job name... ${System.currentTimeMillis()}"

      String nextServerGroupName = resolveJobName(description, submitJobRequest, task, titusClient)
      String jobUri
      int retryCount = 0

      retrySupport.retry({
        try {
          task.updateStatus BASE_PHASE, "Submitting job request to Titus... ${System.currentTimeMillis()}"
          jobUri = titusClient.submitJob(submitJobRequest)
        } catch (io.grpc.StatusRuntimeException e) {
          task.updateStatus BASE_PHASE, "Error encountered submitting job request to Titus ${e.message} for ${nextServerGroupName} ${System.currentTimeMillis()}"
          if (description.jobType == 'service' && (e.status.code == Status.RESOURCE_EXHAUSTED.code || e.status.code == Status.INVALID_ARGUMENT.code) && (e.status.description.contains("Job sequence id reserved by another pending job") || e.status.description.contains("Constraint violation - job with group sequence"))) {
            if (e.status.description.contains("Job sequence id reserved by another pending job")) {
              sleep 1000 ^ Math.pow(2, retryCount)
              retryCount++
            }
            nextServerGroupName = resolveJobName(description, submitJobRequest, task, titusClient)
            task.updateStatus BASE_PHASE, "Retrying with ${nextServerGroupName} after ${retryCount} attempts ${System.currentTimeMillis()}"
            throw e
          }
          if (e.status.code == Status.UNAVAILABLE.code || e.status.code == Status.DEADLINE_EXCEEDED.code) {
            retryCount++
            task.updateStatus BASE_PHASE, "Retrying after ${retryCount} attempts ${System.currentTimeMillis()}"
            throw e
          } else {
            log.error("Could not submit job and not retrying for status ${e.status} ", e)
            task.updateStatus BASE_PHASE, "could not submit job ${e.status} ${e.message} ${System.currentTimeMillis()}"
            throw e
          }
        }
      }, 8, 100, true)

      if (jobUri == null) {
        throw new TitusException("Could not create job")
      }

      task.updateStatus BASE_PHASE, "Successfully submitted job request to Titus (Job URI: ${jobUri}) ${System.currentTimeMillis()}"

      deploymentResult.serverGroupNames = ["${region}:${nextServerGroupName}".toString()]
      deploymentResult.serverGroupNameByRegion = [(description.region): nextServerGroupName]
      deploymentResult.jobUri = jobUri

      if (description.jobType == 'batch') {
        deploymentResult = new TitusDeploymentResult([
          deployedNames          : [jobUri],
          deployedNamesByLocation: [(description.region): [jobUri]],
          jobUri                 : jobUri
        ])
      } else {
        copyScalingPolicies(description, jobUri, nextServerGroupName)
        addLoadBalancers(description, targetGroupLookupResult, jobUri)
      }

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

  private String resolveJobName(TitusDeployDescription description, SubmitJobRequest submitJobRequest, Task task, TitusClient titusClient) {
    if(submitJobRequest.getJobType() == 'batch'){
      submitJobRequest.withJobName(description.application)
      return description.application
    }
    String nextServerGroupName
    TitusServerGroupNameResolver serverGroupNameResolver = new TitusServerGroupNameResolver(titusClient, description.region)
    if (description.sequence != null) {
      nextServerGroupName = serverGroupNameResolver.generateServerGroupName(description.application, description.stack, description.freeFormDetails, description.sequence, false)
    } else {
      nextServerGroupName = serverGroupNameResolver.resolveNextServerGroupName(description.application, description.stack, description.freeFormDetails, false)
    }
    submitJobRequest.withJobName(nextServerGroupName)
    task.updateStatus BASE_PHASE, "Resolved server group name to ${nextServerGroupName} ${System.currentTimeMillis()}"
    return nextServerGroupName
  }

  protected TargetGroupLookupHelper.TargetGroupLookupResult validateLoadBalancers(TitusDeployDescription description) {
    if (!description.targetGroups) {
      return null
    }
    def regionScopedProvider = regionScopedProviderFactory.forRegion(accountCredentialsProvider.getCredentials(description.credentials.awsAccount), description.region)
    def targetGroups = targetGroupLookupHelper.getTargetGroupsByName(regionScopedProvider, description.targetGroups)
    if (targetGroups.unknownTargetGroups) {
      throw new IllegalStateException("Unable to find target groups named $targetGroups.unknownTargetGroups ${System.currentTimeMillis()}")
    }
    return targetGroups
  }

  protected void addLoadBalancers(TitusDeployDescription description, TargetGroupLookupHelper.TargetGroupLookupResult targetGroups, String jobUri) {
    TitusLoadBalancerClient loadBalancerClient = titusClientProvider.getTitusLoadBalancerClient(description.credentials, description.region)
    if (!loadBalancerClient) {
      task.updateStatus BASE_PHASE, "Unable to create load balancing client in target account/region"
      return
    }
    targetGroups?.targetGroupARNs.each { targetGroupARN ->
      loadBalancerClient.addLoadBalancer(jobUri, targetGroupARN)
      task.updateStatus BASE_PHASE, "Attached ${targetGroupARN} to ${jobUri}  ${System.currentTimeMillis()}"
    }
  }

  protected void copyScalingPolicies(TitusDeployDescription description, String jobUri, String serverGroupName) {
    if (!description.copySourceScalingPolicies || !description.copySourceScalingPoliciesAndActions) {
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
              .setScalingPolicy(UpsertTitusScalingPolicyDescription.fromScalingPolicyResult(description.region, policy, serverGroupName).toScalingPolicyBuilder())
            task.updateStatus BASE_PHASE, "Creating new policy copied from policy ${policy.id}"
            autoscalingClient.createScalingPolicy(requestBuilder.build())
          }
        }
      }
    }
    task.updateStatus BASE_PHASE, "Copy scaling policies succeeded (Job URI: ${jobUri}) ${System.currentTimeMillis()}"
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
