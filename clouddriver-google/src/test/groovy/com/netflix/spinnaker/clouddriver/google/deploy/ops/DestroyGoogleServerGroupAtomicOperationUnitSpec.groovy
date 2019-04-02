      /*
 * Copyright 2014 Google, Inc.
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

      import com.google.api.client.googleapis.json.GoogleJsonError
      import com.google.api.client.googleapis.json.GoogleJsonResponseException
      import com.google.api.client.http.HttpHeaders
      import com.google.api.client.http.HttpResponseException
      import com.google.api.services.compute.Compute
      import com.google.api.services.compute.model.*
      import com.netflix.frigga.Names
      import com.netflix.spectator.api.DefaultRegistry
      import com.netflix.spinnaker.clouddriver.data.task.Task
      import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
      import com.netflix.spinnaker.clouddriver.google.GoogleApiTestUtils
      import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
      import com.netflix.spinnaker.clouddriver.google.deploy.GCEUtil
      import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
      import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
      import com.netflix.spinnaker.clouddriver.google.deploy.description.DestroyGoogleServerGroupDescription
      import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
      import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
      import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
      import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer
      import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
      import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
      import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
      import spock.lang.Shared
      import spock.lang.Specification
      import spock.lang.Subject
      import spock.lang.Unroll

      class DestroyGoogleServerGroupAtomicOperationUnitSpec extends Specification {
  private static final ACCOUNT_NAME = "auto"
  private static final PROJECT_NAME = "my_project"
  private static final SERVER_GROUP_NAME = "spinnaker-test-v000"
  private static final APPLICATION_NAME = Names.parseName(SERVER_GROUP_NAME).app
  private static final INSTANCE_TEMPLATE_NAME = "$SERVER_GROUP_NAME-${System.currentTimeMillis()}"
  private static final INSTANCE_GROUP_OP_NAME = "spinnaker-test-v000-op"
  private static final AUTOSCALERS_OP_NAME = "spinnaker-test-v000-autoscaler-op"
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-b"
  private static final DONE = "DONE"

  @Shared
  def threadSleeperMock = Mock(GoogleOperationPoller.ThreadSleeper)
  @Shared
  SafeRetry safeRetry

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))

    // Yes this can affect other tests; but only in a good way.
    safeRetry = new SafeRetry(maxRetries: 10, maxWaitInterval: 60000, retryIntervalBase: 0, jitterMultiplier: 0)
  }

  void "should delete managed instance group"() {
    setup:
      def registry = new DefaultRegistry()
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup =
        new GoogleServerGroup(region: REGION,
                              zone: ZONE,
                              launchConfig: [instanceTemplate: new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME)]).view
      def computeMock = Mock(Compute)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def zoneOperations = Mock(Compute.ZoneOperations)
      def zoneOperationsGet = Mock(Compute.ZoneOperations.Get)
      def instanceGroupManagersDeleteMock = Mock(Compute.InstanceGroupManagers.Delete)
      def instanceGroupManagersDeleteOp = new Operation(name: INSTANCE_GROUP_OP_NAME, status: DONE)
      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
      def targetSslProxies = Mock(Compute.TargetSslProxies)
      def targetSslProxiesList = Mock(Compute.TargetSslProxies.List)
      def targetTcpProxies = Mock(Compute.TargetTcpProxies)
      def targetTcpProxiesList = Mock(Compute.TargetTcpProxies.List)

      googleLoadBalancerProviderMock.getApplicationLoadBalancers(APPLICATION_NAME) >> []
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DestroyGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new DestroyGoogleServerGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry
      operation.googleClusterProvider = googleClusterProviderMock
      operation.googleLoadBalancerProvider = googleLoadBalancerProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
      1 * instanceGroupManagersMock.delete(PROJECT_NAME, ZONE, SERVER_GROUP_NAME) >> instanceGroupManagersDeleteMock
      1 * instanceGroupManagersDeleteMock.execute() >> instanceGroupManagersDeleteOp

      1 * computeMock.zoneOperations() >> zoneOperations
      1 * zoneOperations.get(PROJECT_NAME, ZONE, INSTANCE_GROUP_OP_NAME) >> zoneOperationsGet
      1 * zoneOperationsGet.execute() >> instanceGroupManagersDeleteOp

      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.delete(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()

      1 * computeMock.targetSslProxies() >> targetSslProxies
      1 * targetSslProxies.list(PROJECT_NAME) >> targetSslProxiesList
      1 * targetSslProxiesList.execute() >> new TargetSslProxyList(items: [])

      1 * computeMock.targetTcpProxies() >> targetTcpProxies
      1 * targetTcpProxies.list(PROJECT_NAME) >> targetTcpProxiesList
      1 * targetTcpProxiesList.execute() >> new TargetTcpProxyList(items: [])

      3 * computeMock.globalForwardingRules() >> globalForwardingRules
      3 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      3 * globalForwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, _) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [])
  }

  @Unroll
  void "should delete managed instance group and autoscaler if defined"() {
    setup:
      def registry = new DefaultRegistry()
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def serverGroup =
        new GoogleServerGroup(region: REGION,
                              regional: isRegional,
                              zone: ZONE,
                              launchConfig: [instanceTemplate: new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME)],
                              autoscalingPolicy: [coolDownPeriodSec: 45,
                                                  minNumReplicas: 2,
                                                  maxNumReplicas: 5]).view
      def computeMock = Mock(Compute)
      def regionInstanceGroupManagersMock = Mock(Compute.RegionInstanceGroupManagers)
      def instanceGroupManagersMock = Mock(Compute.InstanceGroupManagers)
      def regionOperations = Mock(Compute.RegionOperations)
      def regionOperationsGet = Mock(Compute.RegionOperations.Get)
      def zoneOperations = Mock(Compute.ZoneOperations)
      def zoneOperationsGet = Mock(Compute.ZoneOperations.Get)
      def regionInstanceGroupManagersDeleteMock = Mock(Compute.RegionInstanceGroupManagers.Delete)
      def regionalInstanceGroupTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.regionInstanceGroupManagers.delete",
            [scope: "regional", region: REGION])
      def instanceGroupManagersDeleteMock = Mock(Compute.InstanceGroupManagers.Delete)
      def instanceGroupManagersDeleteOp = new Operation(name: INSTANCE_GROUP_OP_NAME, status: DONE)
      def zonalInstanceGroupTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.instanceGroupManagers.delete",
            [scope: "zonal", zone: ZONE])

      def instanceTemplatesMock = Mock(Compute.InstanceTemplates)
      def instanceTemplatesDeleteMock = Mock(Compute.InstanceTemplates.Delete)
      def regionAutoscalersMock = Mock(Compute.RegionAutoscalers)
      def regionAutoscalersDeleteMock = Mock(Compute.RegionAutoscalers.Delete)
      def regionalAutoscalerTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.regionAutoscalers.delete",
            [scope: "regional", region: REGION])
      def autoscalersMock = Mock(Compute.Autoscalers)
      def autoscalersDeleteMock = Mock(Compute.Autoscalers.Delete)
      def autoscalersDeleteOp = new Operation(name: AUTOSCALERS_OP_NAME, status: DONE)
      def zonalAutoscalerTimerId = GoogleApiTestUtils.makeOkId(
            registry, "compute.autoscalers.delete",
            [scope: "zonal", zone: ZONE])

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
      def targetSslProxies = Mock(Compute.TargetSslProxies)
      def targetSslProxiesList = Mock(Compute.TargetSslProxies.List)
      def targetTcpProxies = Mock(Compute.TargetTcpProxies)
      def targetTcpProxiesList = Mock(Compute.TargetTcpProxies.List)

      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def description = new DestroyGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)
      googleLoadBalancerProviderMock.getApplicationLoadBalancers(APPLICATION_NAME) >> []
      @Subject def operation = new DestroyGoogleServerGroupAtomicOperation(description)
      operation.googleOperationPoller =
        new GoogleOperationPoller(
          googleConfigurationProperties: new GoogleConfigurationProperties(),
          threadSleeper: threadSleeperMock,
          registry: registry,
          safeRetry: safeRetry
        )
      operation.registry = registry
      operation.safeRetry = safeRetry
      operation.googleClusterProvider = googleClusterProviderMock
      operation.googleLoadBalancerProvider = googleLoadBalancerProviderMock

    when:
      operation.operate([])

    then:
      1 * googleClusterProviderMock.getServerGroup(ACCOUNT_NAME, REGION, SERVER_GROUP_NAME) >> serverGroup

      3 * computeMock.globalForwardingRules() >> globalForwardingRules
      3 * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      3 * globalForwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      1 * computeMock.targetSslProxies() >> targetSslProxies
      1 * targetSslProxies.list(PROJECT_NAME) >> targetSslProxiesList
      1 * targetSslProxiesList.execute() >> new TargetSslProxyList(items: [])

      1 * computeMock.targetTcpProxies() >> targetTcpProxies
      1 * targetTcpProxies.list(PROJECT_NAME) >> targetTcpProxiesList
      1 * targetTcpProxiesList.execute() >> new TargetTcpProxyList(items: [])

      1 * computeMock.forwardingRules() >> forwardingRules
      1 * forwardingRules.list(PROJECT_NAME, _) >> forwardingRulesList
      1 * forwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      if (isRegional) {
        1 * computeMock.regionAutoscalers() >> regionAutoscalersMock
        1 * regionAutoscalersMock.delete(PROJECT_NAME, location, SERVER_GROUP_NAME) >> regionAutoscalersDeleteMock
        1 * regionAutoscalersDeleteMock.execute() >> autoscalersDeleteOp

        1 * computeMock.regionOperations() >> regionOperations
        1 * regionOperations.get(PROJECT_NAME, location, AUTOSCALERS_OP_NAME) >> regionOperationsGet
        1 * regionOperationsGet.execute() >> autoscalersDeleteOp
      } else {
        1 * computeMock.autoscalers() >> autoscalersMock
        1 * autoscalersMock.delete(PROJECT_NAME, location, SERVER_GROUP_NAME) >> autoscalersDeleteMock
        1 * autoscalersDeleteMock.execute() >> autoscalersDeleteOp

        1 * computeMock.zoneOperations() >> zoneOperations
        1 * zoneOperations.get(PROJECT_NAME, location, AUTOSCALERS_OP_NAME) >> zoneOperationsGet
        1 * zoneOperationsGet.execute() >> autoscalersDeleteOp
      }
      registry.timer(regionalAutoscalerTimerId).count() == (isRegional ? 1 : 0)
      registry.timer(zonalAutoscalerTimerId).count() == (isRegional ? 0 : 1)

    then:
      if (isRegional) {
        1 * computeMock.regionInstanceGroupManagers() >> regionInstanceGroupManagersMock
        1 * regionInstanceGroupManagersMock.delete(PROJECT_NAME, location, SERVER_GROUP_NAME) >> regionInstanceGroupManagersDeleteMock
        1 * regionInstanceGroupManagersDeleteMock.execute() >> instanceGroupManagersDeleteOp

        1 * computeMock.regionOperations() >> regionOperations
        1 * regionOperations.get(PROJECT_NAME, location, INSTANCE_GROUP_OP_NAME) >> regionOperationsGet
        1 * regionOperationsGet.execute() >> instanceGroupManagersDeleteOp
      } else {
        1 * computeMock.instanceGroupManagers() >> instanceGroupManagersMock
        1 * instanceGroupManagersMock.delete(PROJECT_NAME, location, SERVER_GROUP_NAME) >> instanceGroupManagersDeleteMock
        1 * instanceGroupManagersDeleteMock.execute() >> instanceGroupManagersDeleteOp

        1 * computeMock.zoneOperations() >> zoneOperations
        1 * zoneOperations.get(PROJECT_NAME, location, INSTANCE_GROUP_OP_NAME) >> zoneOperationsGet
        1 * zoneOperationsGet.execute() >> instanceGroupManagersDeleteOp
      }
      registry.timer(regionalInstanceGroupTimerId).count() == (isRegional ? 1 : 0)
      registry.timer(zonalInstanceGroupTimerId).count() == (isRegional ? 0 : 1)

    then:
      1 * computeMock.instanceTemplates() >> instanceTemplatesMock
      1 * instanceTemplatesMock.delete(PROJECT_NAME, INSTANCE_TEMPLATE_NAME) >> instanceTemplatesDeleteMock
      1 * instanceTemplatesDeleteMock.execute()

    where:
      isRegional | location
      false      | ZONE
      true       | REGION
  }

  @Unroll
  void "should delete http loadbalancer backend if associated"() {
    setup:
      def registry = new DefaultRegistry()
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def loadBalancerNameList = lbNames
      def serverGroup =
          new GoogleServerGroup(
              name: SERVER_GROUP_NAME,
              region: REGION,
              regional: isRegional,
              zone: ZONE,
              asg: [
                  (GCEUtil.GLOBAL_LOAD_BALANCER_NAMES): loadBalancerNameList,
              ],
              launchConfig: [
                  instanceTemplate: new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME,
                      properties: [
                          'metadata': new Metadata(items: [
                              new Metadata.Items(
                                  key: (GCEUtil.GLOBAL_LOAD_BALANCER_NAMES),
                                  value: 'spinnaker-http-load-balancer'
                              ),
                              new Metadata.Items(
                                  key: (GCEUtil.BACKEND_SERVICE_NAMES),
                                  value: 'backend-service'
                              )
                          ])
                      ])
              ]).view
      def computeMock = Mock(Compute)
      def backendServicesMock = Mock(Compute.BackendServices)
      def backendSvcGetMock = Mock(Compute.BackendServices.Get)
      def backendUpdateMock = Mock(Compute.BackendServices.Update)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)

      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)
      googleLoadBalancerProviderMock.getApplicationLoadBalancers("") >> loadBalancerList
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def task = Mock(Task)
      def bs = isRegional ?
          new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildZonalServerGroupUrl(PROJECT_NAME, ZONE, serverGroup.name)) }) :
          new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildRegionalServerGroupUrl(PROJECT_NAME, REGION, serverGroup.name)) })

      def description = new DestroyGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
                                                                region: REGION,
                                                                accountName: ACCOUNT_NAME,
                                                                credentials: credentials)
      @Subject def operation = new DestroyGoogleServerGroupAtomicOperation(description)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      def updateOpName = 'updateOp'

      operation.registry = registry
      operation.safeRetry = safeRetry
      operation.googleClusterProvider = googleClusterProviderMock
      operation.googleLoadBalancerProvider = googleLoadBalancerProviderMock

    when:
      def closure = operation.destroyHttpLoadBalancerBackends(computeMock, PROJECT_NAME, serverGroup, googleLoadBalancerProviderMock)
      closure()

    then:
      _ * computeMock.backendServices() >> backendServicesMock
      _ * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      _ * backendSvcGetMock.execute() >> bs
      _ * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock
      _ * backendUpdateMock.execute() >> [name: updateOpName]
      _ * googleOperationPoller.waitForGlobalOperation(computeMock, PROJECT_NAME, updateOpName, null, task, _, _)

      _ * computeMock.globalForwardingRules() >> globalForwardingRules
      _ * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      _ * globalForwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      _ * computeMock.forwardingRules() >> forwardingRules
      _ * forwardingRules.list(PROJECT_NAME, _) >> forwardingRulesList
      _ * forwardingRulesList.execute() >> new ForwardingRuleList(items: [])
      bs.backends.size == 0

    where:
      isRegional | location | loadBalancerList                                                         | lbNames
      false      | ZONE     |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer']
      true       | REGION   |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer']
      false      | ZONE     |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer']
      true       | REGION   |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer']
      false      | ZONE     |  []                                                                      | []
      true       | REGION   |  []                                                                      | []
  }

  @Unroll
  void "should delete internal loadbalancer backend if associated"() {
    setup:
      def registry = new DefaultRegistry()
      def googleClusterProviderMock = Mock(GoogleClusterProvider)
      def loadBalancerNameList = lbNames
      def serverGroup =
        new GoogleServerGroup(
          name: SERVER_GROUP_NAME,
          region: REGION,
          regional: isRegional,
          zone: ZONE,
          asg: [
            (GCEUtil.REGIONAL_LOAD_BALANCER_NAMES): loadBalancerNameList,
          ],
          launchConfig: [
            instanceTemplate: new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME,
              properties: [
                'metadata': new Metadata(items: [
                  new Metadata.Items(
                    key: (GCEUtil.REGIONAL_LOAD_BALANCER_NAMES),
                    value: 'spinnaker-int-load-balancer'
                  )
                ])
              ])
          ]).view
      def computeMock = Mock(Compute)
      def backendServicesMock = Mock(Compute.RegionBackendServices)
      def backendSvcGetMock = Mock(Compute.RegionBackendServices.Get)
      def backendUpdateMock = Mock(Compute.RegionBackendServices.Update)
      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)

      googleLoadBalancerProviderMock.getApplicationLoadBalancers("") >> loadBalancerList
      def credentials = new GoogleNamedAccountCredentials.Builder().project(PROJECT_NAME).compute(computeMock).build()
      def bs = isRegional ?
        new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildZonalServerGroupUrl(PROJECT_NAME, ZONE, serverGroup.name)) }) :
        new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildRegionalServerGroupUrl(PROJECT_NAME, REGION, serverGroup.name)) })

      def description = new DestroyGoogleServerGroupDescription(serverGroupName: SERVER_GROUP_NAME,
        region: REGION,
        accountName: ACCOUNT_NAME,
        credentials: credentials)
      @Subject def operation = new DestroyGoogleServerGroupAtomicOperation(description)

      def task = Mock(Task)
      def googleOperationPoller = Mock(GoogleOperationPoller)
      operation.googleOperationPoller = googleOperationPoller
      def updateOpName = 'updateOp'

      operation.registry = registry
      operation.safeRetry = safeRetry
      operation.googleClusterProvider = googleClusterProviderMock
      operation.googleLoadBalancerProvider = googleLoadBalancerProviderMock

    when:
      def closure = operation.destroyInternalLoadBalancerBackends(computeMock, PROJECT_NAME, serverGroup, googleLoadBalancerProviderMock)
      closure()

    then:
      _ * computeMock.regionBackendServices() >> backendServicesMock
      _ * backendServicesMock.get(PROJECT_NAME, REGION, 'backend-service') >> backendSvcGetMock
      _ * backendSvcGetMock.execute() >> bs
      _ * backendServicesMock.update(PROJECT_NAME, REGION, 'backend-service', bs) >> backendUpdateMock
      _ * backendUpdateMock.execute() >> [name: updateOpName]
      _ * googleOperationPoller.waitForRegionalOperation(computeMock, PROJECT_NAME, REGION, updateOpName, null, task, _, _)

      _ * computeMock.globalForwardingRules() >> globalForwardingRules
      _ * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      _ * globalForwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      _ * computeMock.forwardingRules() >> forwardingRules
      _ * forwardingRules.list(PROJECT_NAME, _) >> forwardingRulesList
      _ * forwardingRulesList.execute() >> new ForwardingRuleList(items: [])
      bs.backends.size == 0

    where:
      isRegional | location | loadBalancerList                                                                                                                               | lbNames
      false      | ZONE     | [new GoogleInternalLoadBalancer(name: 'spinnaker-int-load-balancer', backendService: new GoogleBackendService(name: 'backend-service')).view] | ['spinnaker-int-load-balancer']
      true       | REGION   | [new GoogleInternalLoadBalancer(name: 'spinnaker-int-load-balancer', backendService: new GoogleBackendService(name: 'backend-service')).view] | ['spinnaker-int-load-balancer']
      false      | ZONE     | [new GoogleInternalLoadBalancer(name: 'spinnaker-int-load-balancer', backendService: new GoogleBackendService(name: 'backend-service')).view] | ['spinnaker-int-load-balancer']
      true       | REGION   | [new GoogleInternalLoadBalancer(name: 'spinnaker-int-load-balancer', backendService: new GoogleBackendService(name: 'backend-service')).view] | ['spinnaker-int-load-balancer']
      false      | ZONE     | []                                                                                                                                             | []
      true       | REGION   | []                                                                                                                                             | []
  }

  void "should retry http backend deletion on 400, 412, socket timeout, succeed on 404"() {
    // Note: Implicitly tests SafeRetry.doRetry
    setup:
      def registry = new DefaultRegistry()
      def computeMock = Mock(Compute)
      def backendServicesMock = Mock(Compute.BackendServices)
      def backendSvcGetMock = Mock(Compute.BackendServices.Get)
      def backendUpdateMock = Mock(Compute.BackendServices.Update)
      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)
      googleLoadBalancerProviderMock.getApplicationLoadBalancers("") >> loadBalancerList

      def serverGroup =
          new GoogleServerGroup(
              name: SERVER_GROUP_NAME,
              region: REGION,
              regional: isRegional,
              zone: ZONE,
              asg: [
                  (GCEUtil.GLOBAL_LOAD_BALANCER_NAMES): lbNames,
              ],
              launchConfig: [
                  instanceTemplate: new InstanceTemplate(name: INSTANCE_TEMPLATE_NAME,
                      properties: [
                          'metadata': new Metadata(items: [
                              new Metadata.Items(
                                  key: (GCEUtil.GLOBAL_LOAD_BALANCER_NAMES),
                                  value: 'spinnaker-http-load-balancer'
                              ),
                              new Metadata.Items(
                                  key: (GCEUtil.BACKEND_SERVICE_NAMES),
                                  value: 'backend-service'
                              )
                          ])
                      ])
              ]).view

      def errorMessage = "The resource 'my-backend-service' is not ready"
      def errorInfo = new GoogleJsonError.ErrorInfo(
          domain: "global",
          message: errorMessage,
          reason: "resourceNotReady")
      def details = new GoogleJsonError(
          code: 400,
          errors: [errorInfo],
          message: errorMessage)
      def httpResponseExceptionBuilder = new HttpResponseException.Builder(
          400,
          "Bad Request",
          new HttpHeaders()).setMessage("400 Bad Request")
      def googleJsonResponseException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)

      errorMessage = "Invalid fingerprint."
      errorInfo = new GoogleJsonError.ErrorInfo(
          domain: "global",
          message: errorMessage,
          reason: "conditionNotMet")
      details = new GoogleJsonError(
          code: 412,
          errors: [errorInfo],
          message: errorMessage)
      httpResponseExceptionBuilder = new HttpResponseException.Builder(
          412,
          "Precondition Failed",
          new HttpHeaders()).setMessage("412 Precondition Failed")
      def fingerPrintException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)

      errorMessage = "Resource 'stuff' could not be located"
      errorInfo = new GoogleJsonError.ErrorInfo(
          domain: "global",
          message: errorMessage,
          reason: "stuffNotFound")
      details = new GoogleJsonError(
          code: 404,
          errors: [errorInfo],
          message: errorMessage)
      httpResponseExceptionBuilder = new HttpResponseException.Builder(
          404,
          "Not Found",
          new HttpHeaders()).setMessage("404 Not Found")
      def notFoundException = new GoogleJsonResponseException(httpResponseExceptionBuilder, details)

      def socketTimeoutException = new SocketTimeoutException("Read timed out")

      def bs = isRegional ?
          new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildZonalServerGroupUrl(PROJECT_NAME, ZONE, serverGroup.name)) }) :
          new BackendService(backends: lbNames.collect { new Backend(group: GCEUtil.buildRegionalServerGroupUrl(PROJECT_NAME, REGION, serverGroup.name)) })
      def updateOpName = 'updateOp'
      def task = Mock(Task)
      def googleOperationPoller = Mock(GoogleOperationPoller)

    when:
      def destroy = new DestroyGoogleServerGroupAtomicOperation()

      destroy.googleOperationPoller = googleOperationPoller

      destroy.registry = registry
      destroy.safeRetry = safeRetry
      destroy.destroy(
          destroy.destroyHttpLoadBalancerBackends(computeMock, PROJECT_NAME, serverGroup, googleLoadBalancerProviderMock),
          "Http load balancer backends", [action: 'test']
      )

    then:
      1 * backendUpdateMock.execute() >> { throw googleJsonResponseException }
      2 * computeMock.backendServices() >> backendServicesMock
      1 * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      1 * backendSvcGetMock.execute() >> bs
      1 * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock

    then:
      1 * backendUpdateMock.execute() >> { throw fingerPrintException }
      2 * computeMock.backendServices() >> backendServicesMock
      1 * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      1 * backendSvcGetMock.execute() >> bs
      1 * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock

    then:
      1 * backendUpdateMock.execute() >> { throw socketTimeoutException }
      2 * computeMock.backendServices() >> backendServicesMock
      1 * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      1 * backendSvcGetMock.execute() >> bs
      1 * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock

    then:
      1 * backendUpdateMock.execute() >> [name: updateOpName]
      2 * computeMock.backendServices() >> backendServicesMock
      1 * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      1 * backendSvcGetMock.execute() >> bs
      1 * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock
      _ * googleOperationPoller.waitForGlobalOperation(computeMock, PROJECT_NAME, updateOpName, null, task, _, _)

    when:
      destroy.destroy(
        destroy.destroyHttpLoadBalancerBackends(computeMock, PROJECT_NAME, serverGroup, googleLoadBalancerProviderMock),
        "Http load balancer backends",  [action: 'test']
      )

    then:
      1 * backendUpdateMock.execute() >> { throw notFoundException }
      2 * computeMock.backendServices() >> backendServicesMock
      1 * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      1 * backendSvcGetMock.execute() >> bs
      1 * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock

    where:
      isRegional | location | loadBalancerList                                                         | lbNames
      false      | ZONE     |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer']
  }
}
