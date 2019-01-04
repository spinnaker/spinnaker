/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.google.deploy.ops

import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleApiTestUtils
import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
import com.netflix.spinnaker.clouddriver.google.deploy.description.UpsertGoogleAutoscalingPolicyDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoHealingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy.CustomMetricUtilization.UtilizationTargetType
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class UpsertGoogleAutoscalingPolicyAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my-project"
  private static final SERVER_GROUP_NAME = "server-group-name"
  private static final ZONE = "us-central1-f"
  private static final METRIC = "my-metric"
  private static final METRIC_2 = "my-other-metric"
  private static final UTILIZATION_TARGET = 0.6
  private static final UTILIZATION_TARGET_2 = 0.6
  private static final MIN_NUM_REPLICAS = 1
  private static final MIN_NUM_REPLICAS_2 = 2
  private static final MAX_NUM_REPLICAS = 10
  private static final MAX_NUM_REPLICAS_2 = 11
  private static final COOL_DOWN_PERIOD_SEC = 60
  private static final COOL_DOWN_PERIOD_SEC_2 = 61
  private static final CPU_UTILIZATION = new GoogleAutoscalingPolicy.CpuUtilization(
    utilizationTarget: UTILIZATION_TARGET)
  private static final LOAD_BALANCING_UTILIZATION = new GoogleAutoscalingPolicy.LoadBalancingUtilization(
    utilizationTarget: UTILIZATION_TARGET)
  private static final CUSTOM_METRIC_UTILIZATIONS = [new GoogleAutoscalingPolicy.CustomMetricUtilization(
    metric: METRIC,
    utilizationTarget: UTILIZATION_TARGET, utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE)]
  private static final GOOGLE_SCALING_POLICY = new GoogleAutoscalingPolicy(minNumReplicas: MIN_NUM_REPLICAS,
    maxNumReplicas: MAX_NUM_REPLICAS,
    coolDownPeriodSec: COOL_DOWN_PERIOD_SEC,
    cpuUtilization: CPU_UTILIZATION,
    loadBalancingUtilization: LOAD_BALANCING_UTILIZATION,
    customMetricUtilizations: CUSTOM_METRIC_UTILIZATIONS)
  private static
  final SELF_LINK = "https://www.googleapis.com/compute/v1/projects/shared-spinnaker/zones/us-central1-f/instances/my-app7-dev-v000-1"
  private static final REGION = "us-central1"
  private static final AUTOSCALER = GCEUtil.buildAutoscaler(SERVER_GROUP_NAME, SELF_LINK, GOOGLE_SCALING_POLICY)

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  @Unroll
  void "can create zonal and regional scaling policies"() {
    setup:
    def registry = new DefaultRegistry()
    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def serverGroup = new GoogleServerGroup(zone: ZONE, regional: isRegional, selfLink: SELF_LINK).view
    def computeMock = Mock(Compute)

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: GOOGLE_SCALING_POLICY,
      credentials: credentials)

    // zonal setup
    def autoscalerMock = Mock(Compute.Autoscalers)
    def insertMock = Mock(Compute.Autoscalers.Insert)
    def zonalTimerId = GoogleApiTestUtils.makeOkId(registry, "compute.autoscalers.insert", [scope: "zonal", zone: ZONE])
    registry.timer(zonalTimerId)

    // regional setup
    def regionAutoscalerMock = Mock(Compute.RegionAutoscalers)
    def regionInsertMock = Mock(Compute.RegionAutoscalers.Insert)
    def regionalTimerId = GoogleApiTestUtils.makeOkId(registry, "compute.regionAutoscalers.insert", [scope: "regional", region: REGION])
    registry.timer(regionalTimerId)

    @Subject def operation = Spy(UpsertGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * operation.updatePolicyMetadata(computeMock, credentials, PROJECT_NAME, _, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalerMock
      1 * regionAutoscalerMock.insert(PROJECT_NAME, location, AUTOSCALER) >> regionInsertMock
      1 * regionInsertMock.execute()
    } else {
      1 * computeMock.autoscalers() >> autoscalerMock
      1 * autoscalerMock.insert(PROJECT_NAME, location, AUTOSCALER) >> insertMock
      1 * insertMock.execute()
    }

    registry.timer(regionalTimerId).count() == (isRegional ? 1 : 0)
    registry.timer(zonalTimerId).count() == (isRegional ? 0 : 1)

    where:
    isRegional | location
    false      | ZONE
    true       | REGION
  }

  @Unroll
  void "can update zonal and regional scaling policies"() {
    given:
    def registry = new DefaultRegistry()
    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def computeMock = Mock(Compute)
    def autoscalingPolicy = new AutoscalingPolicy(
      minNumReplicas: 1,
      maxNumReplicas: 10,
      coolDownPeriodSec: 60);
    def serverGroup = new GoogleServerGroup(
      zone: ZONE, regional: isRegional, selfLink: SELF_LINK, autoscalingPolicy: autoscalingPolicy).view

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: GOOGLE_SCALING_POLICY,
      credentials: credentials)

    // zonal setup
    def autoscalerMock = Mock(Compute.Autoscalers)
    def updateMock = Mock(Compute.Autoscalers.Update)
    def zonalTimerId = GoogleApiTestUtils.makeOkId(registry, "compute.autoscalers.update", [scope: "zonal", zone: ZONE])

    // regional setup
    def regionAutoscalerMock = Mock(Compute.RegionAutoscalers)
    def regionUpdateMock = Mock(Compute.RegionAutoscalers.Update)
    def regionalTimerId = GoogleApiTestUtils.makeOkId(registry, "compute.regionAutoscalers.update", [scope: "regional", region: REGION])

    @Subject def operation = Spy(UpsertGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * operation.updatePolicyMetadata(computeMock, credentials, PROJECT_NAME, _, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalerMock
      1 * regionAutoscalerMock.update(PROJECT_NAME, location, AUTOSCALER) >> regionUpdateMock
      1 * regionUpdateMock.execute()
    } else {
      1 * computeMock.autoscalers() >> autoscalerMock
      1 * autoscalerMock.update(PROJECT_NAME, location, AUTOSCALER) >> updateMock
      1 * updateMock.execute()
    }
    registry.timer(regionalTimerId).count() == (isRegional ? 1 : 0)
    registry.timer(zonalTimerId).count() == (isRegional ? 0 : 1)

    where:
    isRegional | location
    false      | ZONE
    true       | REGION
  }

  @Unroll
  void "builds autoscaler based on ancestor autoscaling policy and input description: input overrides nothing"() {
    setup:
    def registry = new DefaultRegistry()
    def ancestorPolicy = new AutoscalingPolicy(
      minNumReplicas: MIN_NUM_REPLICAS, maxNumReplicas: MAX_NUM_REPLICAS, coolDownPeriodSec: COOL_DOWN_PERIOD_SEC,
      cpuUtilization: new AutoscalingPolicyCpuUtilization(utilizationTarget: UTILIZATION_TARGET),
      loadBalancingUtilization: new AutoscalingPolicyLoadBalancingUtilization(utilizationTarget: UTILIZATION_TARGET),
      customMetricUtilizations: [new AutoscalingPolicyCustomMetricUtilization(
        metric: METRIC,
        utilizationTarget: UTILIZATION_TARGET,
        utilizationTargetType: "DELTA_PER_MINUTE")]);
    def ancestorDescription = GCEUtil.buildAutoscalingPolicyDescriptionFromAutoscalingPolicy(ancestorPolicy)

    def updatePolicy = new GoogleAutoscalingPolicy()

    def expectedAutoscaler = GCEUtil.buildAutoscaler(
      SERVER_GROUP_NAME, SELF_LINK, ancestorDescription)

    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def computeMock = Mock(Compute)
    def serverGroup = new GoogleServerGroup(
      zone: ZONE,
      selfLink: SELF_LINK,
      regional: isRegional,
      autoscalingPolicy: ancestorPolicy).view

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: updatePolicy,
      credentials: credentials)

    // zonal setup
    def autoscalerMock = Mock(Compute.Autoscalers)
    def updateMock = Mock(Compute.Autoscalers.Update)

    // regional setup
    def regionAutoscalerMock = Mock(Compute.RegionAutoscalers)
    def regionUpdateMock = Mock(Compute.RegionAutoscalers.Update)

    @Subject def operation = Spy(UpsertGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * operation.updatePolicyMetadata(computeMock, credentials, PROJECT_NAME, _, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup
    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalerMock
      1 * regionAutoscalerMock.update(PROJECT_NAME, location, expectedAutoscaler) >> regionUpdateMock
      1 * regionUpdateMock.execute()
    } else {
      1 * computeMock.autoscalers() >> autoscalerMock
      1 * autoscalerMock.update(PROJECT_NAME, location, expectedAutoscaler) >> updateMock
      1 * updateMock.execute()
    }

    where:
    isRegional | location
    false      | ZONE
    true       | REGION
  }

  @Unroll
  void "builds autoscaler based on ancestor autoscaling policy and input description; input overrides everything"() {
    setup:
    def registry = new DefaultRegistry()
    def ancestorPolicy = new AutoscalingPolicy(
      minNumReplicas: MIN_NUM_REPLICAS, maxNumReplicas: MAX_NUM_REPLICAS, coolDownPeriodSec: COOL_DOWN_PERIOD_SEC,
      cpuUtilization: new AutoscalingPolicyCpuUtilization(utilizationTarget: UTILIZATION_TARGET),
      loadBalancingUtilization: new AutoscalingPolicyLoadBalancingUtilization(utilizationTarget: UTILIZATION_TARGET),
      customMetricUtilizations: [new AutoscalingPolicyCustomMetricUtilization(
        metric: METRIC,
        utilizationTarget: UTILIZATION_TARGET,
        utilizationTargetType: UtilizationTargetType.DELTA_PER_SECOND)]);

    def updatePolicy = new GoogleAutoscalingPolicy(minNumReplicas: MIN_NUM_REPLICAS_2,
      maxNumReplicas: MAX_NUM_REPLICAS_2,
      coolDownPeriodSec: COOL_DOWN_PERIOD_SEC_2,
      cpuUtilization: new GoogleAutoscalingPolicy.CpuUtilization(
        utilizationTarget: UTILIZATION_TARGET_2),
      loadBalancingUtilization: new GoogleAutoscalingPolicy.LoadBalancingUtilization(
        utilizationTarget: UTILIZATION_TARGET_2),
      customMetricUtilizations: [new GoogleAutoscalingPolicy.CustomMetricUtilization(
        metric: METRIC_2,
        utilizationTargetType: UtilizationTargetType.DELTA_PER_MINUTE,
        utilizationTarget: UTILIZATION_TARGET_2)])

    def expectedAutoscaler = GCEUtil.buildAutoscaler(
      SERVER_GROUP_NAME, SELF_LINK, updatePolicy)

    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def computeMock = Mock(Compute)
    def serverGroup = new GoogleServerGroup(zone: ZONE,
      selfLink: SELF_LINK,
      regional: isRegional,
      autoscalingPolicy: ancestorPolicy).view

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: updatePolicy,
      credentials: credentials)

    // zonal setup
    def autoscalerMock = Mock(Compute.Autoscalers)
    def updateMock = Mock(Compute.Autoscalers.Update)

    // regional setup
    def regionAutoscalerMock = Mock(Compute.RegionAutoscalers)
    def regionUpdateMock = Mock(Compute.RegionAutoscalers.Update)

    @Subject def operation = Spy(UpsertGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.operate([])

    then:
    1 * operation.updatePolicyMetadata(computeMock, credentials, PROJECT_NAME, _, _) >> null // Tested separately.
    1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup
    if (isRegional) {
      1 * computeMock.regionAutoscalers() >> regionAutoscalerMock
      1 * regionAutoscalerMock.update(PROJECT_NAME, location, expectedAutoscaler) >> regionUpdateMock
      1 * regionUpdateMock.execute()
    } else {
      1 * computeMock.autoscalers() >> autoscalerMock
      1 * autoscalerMock.update(PROJECT_NAME, location, expectedAutoscaler) >> updateMock
      1 * updateMock.execute()
    }

    where:
    isRegional | location
    false      | ZONE
    true       | REGION
  }

  void "builds autoHealing policy based on ancestor autoHealing policy and input description; overrides everything"() {
    given:
    def ancestorPolicy = new GoogleAutoHealingPolicy(
      healthCheck: 'ancestor',
      initialDelaySec: 100,
      maxUnavailable: new GoogleAutoHealingPolicy.FixedOrPercent(percent: 1)
    )

    def inputDescription = new GoogleAutoHealingPolicy(
      healthCheck: 'update',
      initialDelaySec: 200,
      maxUnavailable: new GoogleAutoHealingPolicy.FixedOrPercent(fixed: 10)
    )

    expect:
    UpsertGoogleAutoscalingPolicyAtomicOperation
      .copyAndOverrideAncestorAutoHealingPolicy(ancestorPolicy, inputDescription) == inputDescription
  }

  void "builds autoHealing policy based on ancestor autoHealing policy and input description; overrides nothing"() {
    given:
    def ancestorPolicy = new GoogleAutoHealingPolicy(
      healthCheck: 'ancestor',
      initialDelaySec: 100,
      maxUnavailable: new GoogleAutoHealingPolicy.FixedOrPercent(percent: 1)
    )

    def inputDescription = new GoogleAutoHealingPolicy(
      healthCheck: null,
      initialDelaySec: null,
      maxUnavailable: null
    )

    expect:
    UpsertGoogleAutoscalingPolicyAtomicOperation
      .copyAndOverrideAncestorAutoHealingPolicy(ancestorPolicy, inputDescription) == ancestorPolicy
  }

  void "if the input description's maxUnavailable is empty object, the resulting policy has no maxUnavailable property"() {
    given:
    def ancestorPolicy = new GoogleAutoHealingPolicy(
      healthCheck: 'ancestor',
      initialDelaySec: 100,
      maxUnavailable: new GoogleAutoHealingPolicy.FixedOrPercent(percent: 1)
    )

    def inputDescription = new GoogleAutoHealingPolicy(
      healthCheck: null,
      initialDelaySec: null,
      maxUnavailable: new GoogleAutoHealingPolicy.FixedOrPercent()
    )

    expect:
    UpsertGoogleAutoscalingPolicyAtomicOperation
      .copyAndOverrideAncestorAutoHealingPolicy(ancestorPolicy, inputDescription).maxUnavailable == null
  }

  void "update the instance template when updatePolicyMetadata is called"() {
    given:
    def registry = new DefaultRegistry()
    def googleClusterProviderMock = Mock(GoogleClusterProvider)
    def computeMock = Mock(Compute)
    def autoscaler = [:]

    def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
    def description = new UpsertGoogleAutoscalingPolicyDescription(
      accountName: ACCOUNT_NAME,
      region: REGION,
      serverGroupName: SERVER_GROUP_NAME,
      autoscalingPolicy: GOOGLE_SCALING_POLICY,
      credentials: credentials)

    // Instance Template Update setup
    def igm = Mock(Compute.InstanceGroupManagers)
    def igmGet = Mock(Compute.InstanceGroupManagers.Get)
    def regionIgm = Mock(Compute.RegionInstanceGroupManagers)
    def regionIgmGet = Mock(Compute.RegionInstanceGroupManagers.Get)
    def groupManager = [instanceTemplate: 'templates/template']
    def instanceTemplates = Mock(Compute.InstanceTemplates)
    def instanceTemplatesGet = Mock(Compute.InstanceTemplates.Get)
    // TODO(jacobkiefer): The following is very change detector-y. Consider a refactor so we can just mock this function.
    def template = new InstanceTemplate(properties: [
      disks: [[getBoot: { return [initializeParams: [sourceImage: 'images/sourceImage']] }, initializeParams: [diskType: 'huge', diskSizeGb: 42], autoDelete: false]],
      name: 'template',
      networkInterfaces: [[network: "projects/$PROJECT_NAME/networks/my-network"]],
      serviceAccounts: [[email: 'serviceAccount@google.com']]
    ])

    @Subject def operation = Spy(UpsertGoogleAutoscalingPolicyAtomicOperation, constructorArgs: [description])
    operation.registry = registry
    operation.googleClusterProvider = googleClusterProviderMock

    when:
    operation.updatePolicyMetadata(computeMock, credentials, PROJECT_NAME, groupUrl, autoscaler)

    then:
    if (isRegional) {
      1 * computeMock.regionInstanceGroupManagers() >> regionIgm
      1 * regionIgm.get(PROJECT_NAME, location, _ ) >> regionIgmGet
      1 * regionIgmGet.execute() >> groupManager
    } else {
      1 * computeMock.instanceGroupManagers() >> igm
      1 * igm.get(PROJECT_NAME, location, _ ) >> igmGet
      1 * igmGet.execute() >> groupManager
    }
    1 * computeMock.instanceTemplates() >> instanceTemplates
    1 * instanceTemplates.get(PROJECT_NAME, _) >> instanceTemplatesGet
    1 * instanceTemplatesGet.execute() >> template

    where:
    isRegional | location | groupUrl
    false      | ZONE     | "https://www.googleapis.com/compute/v1/projects/spinnaker-jtk54/zones/us-central1-f/autoscalers/okra-auto-v005"
    true       | REGION   | "https://www.googleapis.com/compute/v1/projects/spinnaker-jtk54/regions/us-central1/autoscalers/okra-auto-v005"
  }
}
