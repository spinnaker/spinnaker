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

package com.netflix.spinnaker.clouddriver.google.deploy.handlers

import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeRequest
import com.google.api.services.compute.model.Autoscaler
import com.google.api.services.compute.model.Backend
import com.google.api.services.compute.model.BackendService
import com.google.api.services.compute.model.Image
import com.google.api.services.compute.model.ImageList
import com.google.api.services.compute.model.Instance
import com.google.api.services.compute.model.InstanceList
import com.google.api.services.compute.model.MachineType
import com.google.api.services.compute.model.MachineTypeList
import com.google.api.services.compute.model.Network
import com.google.api.services.compute.model.NetworkList
import com.google.api.services.compute.model.Operation
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.deploy.GoogleOperationPoller
import com.netflix.spinnaker.clouddriver.google.deploy.SafeRetry
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancingPolicy
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleBackendService
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.security.GoogleCredentials
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.names.NamerRegistry
import com.netflix.spinnaker.moniker.Namer
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class BasicGoogleDeployHandlerSpec extends Specification {

  @Shared
  BasicGoogleDeployHandler handler

  void setupSpec() {
    this.handler = new BasicGoogleDeployHandler()
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "handler supports basic deploy description type"() {
    given:
    def description = new BasicGoogleDeployDescription()

    expect:
    handler.handles description
  }

  /**
   * TODO: this is a really hard thing to test.
   */
  @Ignore
  void "handler deploys with netflix specific naming convention"() {
    setup:
    def compute = Mock(Compute)
    def instanceMock = getComputeMock(Compute.Instances, Compute.Instances.List, InstanceList, Instance, null)
    def credentials = new GoogleCredentials("project", compute)
    def description = new BasicGoogleDeployDescription(application: "app", stack: "stack", image: "image", instanceType: "f1-micro", zone: "us-central1-b", credentials: credentials)

    when:
    handler.handle(description, [])

    then:
    10 * compute.machineTypes() >> getComputeMock(Compute.MachineTypes, Compute.MachineTypes.List, MachineTypeList, MachineType, description.instanceType)
    10 * compute.images() >> getComputeMock(Compute.Images, Compute.Images.List, ImageList, Image, description.image)
    10 * compute.networks() >> getComputeMock(Compute.Networks, Compute.Networks.List, NetworkList, Network, "default")
    20 * compute.instances() >> instanceMock
    10 * instanceMock.insert(_, _, _) >> Mock(Compute.Instances.Insert)
  }

  def getItem(name, Class type) {
    [getName: { name }, getSelfLink: { "selfLink" }]
  }

  def getComputeMock(Class mockType, Class listType, Class listModelType, Class modelType, String name) {
    def mock = Mock(mockType)
    def list = Mock(listType)
    def listModel = Mock(ComputeRequest)
    listModel.getItems() >> [getItems: getItem(name, modelType)]
    list.execute() >> listModel
    mock.list(_, _) >> list
    mock
  }

  def "getBackendServiceToUpdate returns empty list when no backend service metadata"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    def description = new BasicGoogleDeployDescription()
    def lbInfo = new BasicGoogleDeployHandler.LoadBalancerInfo()
    def policy = new GoogleHttpLoadBalancingPolicy()
    
    // Setup description with no backend service metadata
    description.instanceMetadata = [:]
    
    when:
    def result = handler.getBackendServiceToUpdate(description, "test-server-group", lbInfo, policy, "us-central1")
    
    then:
    result.isEmpty()
  }
  
  def "hasBackedServiceFromInput returns true when backend service names in metadata"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    def description = new BasicGoogleDeployDescription()
    def lbInfo = new BasicGoogleDeployHandler.LoadBalancerInfo()
    
    // Setup description with backend service metadata
    description.instanceMetadata = ["backend-service-names": "test-backend-1"]
    
    when:
    def result = handler.hasBackedServiceFromInput(description, lbInfo)
    
    then:
    result == true
  }
  
  def "hasBackedServiceFromInput returns false when no relevant metadata or load balancers"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    def description = new BasicGoogleDeployDescription()
    def lbInfo = new BasicGoogleDeployHandler.LoadBalancerInfo()
    
    // Setup description with no relevant metadata
    description.instanceMetadata = [:]
    
    when:
    def result = handler.hasBackedServiceFromInput(description, lbInfo)
    
    then:
    result == false
  }
    
  def "autoscaler operations return operations for waiting"() {
    setup:
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    def mockCompute = Mock(Compute)
    def mockAutoscalers = Mock(Compute.Autoscalers)
    def mockAutoscalerInsert = Mock(Compute.Autoscalers.Insert)
    def mockRegionAutoscalers = Mock(Compute.RegionAutoscalers)
    def mockRegAutoscalerInsert = Mock(Compute.RegionAutoscalers.Insert)
    
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def zonalOperation = new Operation(name: "zonal-op", status: "DONE")
    def regionalOperation = new Operation(name: "regional-op", status: "DONE")
    
    mockCompute.autoscalers() >> mockAutoscalers
    mockCompute.regionAutoscalers() >> mockRegionAutoscalers
    mockAutoscalers.insert(_, _, _) >> mockAutoscalerInsert
    mockAutoscalerInsert.execute() >> zonalOperation
    mockRegionAutoscalers.insert(_, _, _) >> mockRegAutoscalerInsert
    mockRegAutoscalerInsert.execute() >> regionalOperation
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    when:
    def zonalResult = handler.timeExecute(mockAutoscalerInsert, "compute.autoscalers.insert", "TAG_SCOPE", "SCOPE_ZONAL")
    def regionalResult = handler.timeExecute(mockRegAutoscalerInsert, "compute.regionAutoscalers.insert", "TAG_SCOPE", "SCOPE_REGIONAL")
    
    then:
    1 * mockAutoscalerInsert.execute() >> zonalOperation
    1 * mockRegAutoscalerInsert.execute() >> regionalOperation
    
    zonalResult.getName() == "zonal-op"
    regionalResult.getName() == "regional-op"
  }

  def "buildLoadBalancerPolicyFromInput initializes policy from description"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    handler.objectMapper = new ObjectMapper()
    
    def description = new BasicGoogleDeployDescription()
    description.instanceMetadata = [:] // Initialize empty metadata map
    def inputPolicy = new GoogleHttpLoadBalancingPolicy(
        balancingMode: GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION,
        maxUtilization: 0.8f,
        capacityScaler: 1.0f
    )
    description.loadBalancingPolicy = inputPolicy
    
    when:
    def result = handler.buildLoadBalancerPolicyFromInput(description)
    
    then:
    result != null
    result.balancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION
    result.maxUtilization == 0.8f
    result.capacityScaler == 1.0f
  }

  def "buildLoadBalancerPolicyFromInput creates default policy when no input provided"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    handler.objectMapper = new ObjectMapper()
    
    def description = new BasicGoogleDeployDescription()
    description.instanceMetadata = [:] // Initialize empty metadata map
    // No load balancing policy set in description
    
    when:
    def result = handler.buildLoadBalancerPolicyFromInput(description)
    
    then:
    result != null
    result.balancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION
    // Should have sensible defaults for HTTP load balancing
    result.maxRatePerInstance == null
    result.maxConnectionsPerInstance == null
    result.maxUtilization != null  // Default for UTILIZATION mode
    result.capacityScaler != null
  }
  
  def "buildLoadBalancerPolicyFromInput uses policy from JSON metadata when available"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    handler.objectMapper = new ObjectMapper()
    
    def description = new BasicGoogleDeployDescription()
    def policyJson = '''{
      "balancingMode": "UTILIZATION",
      "maxUtilization": 0.9,
      "capacityScaler": 0.8
    }'''
    description.instanceMetadata = ["load-balancing-policy": policyJson]
    // No loadBalancingPolicy set in description to test JSON fallback
    
    when:
    def result = handler.buildLoadBalancerPolicyFromInput(description)
    
    then:
    result != null
    result.balancingMode == GoogleLoadBalancingPolicy.BalancingMode.UTILIZATION
    result.maxUtilization == 0.9f
    result.capacityScaler == 0.8f
  }
  
  def "buildLoadBalancerPolicyFromInput handles invalid JSON gracefully"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    handler.objectMapper = new ObjectMapper()
    
    def description = new BasicGoogleDeployDescription()
    description.instanceMetadata = ["load-balancing-policy": "invalid-json{"]
    // No loadBalancingPolicy set in description
    
    when:
    def result = handler.buildLoadBalancerPolicyFromInput(description)
    
    then:
    // Should throw JsonProcessingException for invalid JSON
    thrown(com.fasterxml.jackson.core.JsonProcessingException)
  }
  
  def "buildLoadBalancerPolicyFromInput prioritizes description policy over metadata"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    handler.objectMapper = new ObjectMapper()
    
    def description = new BasicGoogleDeployDescription()
    def inputPolicy = new GoogleHttpLoadBalancingPolicy(
        balancingMode: GoogleLoadBalancingPolicy.BalancingMode.RATE,
        maxRatePerInstance: 1000
    )
    description.loadBalancingPolicy = inputPolicy
    
    // Add JSON metadata that should be ignored since description policy is set
    def policyJson = '''{
      "balancingMode": "UTILIZATION",
      "maxUtilization": 0.9
    }'''
    description.instanceMetadata = ["load-balancing-policy": policyJson]
    
    when:
    def result = handler.buildLoadBalancerPolicyFromInput(description)
    
    then:
    result != null
    result.balancingMode == GoogleLoadBalancingPolicy.BalancingMode.RATE
    result.maxRatePerInstance == 1000
    // Should use description policy, not JSON metadata
    result.maxUtilization == null
  }

  def "getBackendServiceFromProvider retrieves backend service successfully"() {
    given:
    def mockCompute = Mock(Compute)
    def mockBackendServices = Mock(Compute.BackendServices)
    def mockGet = Mock(Compute.BackendServices.Get)
    def expectedBackendService = new BackendService(name: "test-backend-service")
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    mockCompute.backendServices() >> mockBackendServices
    mockBackendServices.get("test-project", "test-backend-service") >> mockGet
    mockGet.execute() >> expectedBackendService
    
    when:
    def result = handler.getBackendServiceFromProvider(credentials, "test-backend-service")
    
    then:
    result == expectedBackendService
    result.name == "test-backend-service"
  }
  
  def "getBackendServiceFromProvider propagates IOException"() {
    given:
    def mockCompute = Mock(Compute)
    def mockBackendServices = Mock(Compute.BackendServices)
    def mockGet = Mock(Compute.BackendServices.Get)
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    mockCompute.backendServices() >> mockBackendServices
    mockBackendServices.get("test-project", "test-backend-service") >> mockGet
    mockGet.execute() >> { throw new IOException("Network error") }
    
    when:
    handler.getBackendServiceFromProvider(credentials, "test-backend-service")
    
    then:
    thrown(IOException)
  }

  def "getRegionBackendServiceFromProvider retrieves regional backend service successfully"() {
    given:
    def mockCompute = Mock(Compute)
    def mockRegionBackendServices = Mock(Compute.RegionBackendServices)
    def mockGet = Mock(Compute.RegionBackendServices.Get)
    def expectedBackendService = new BackendService(name: "test-regional-backend-service")
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    mockCompute.regionBackendServices() >> mockRegionBackendServices
    mockRegionBackendServices.get("test-project", "us-central1", "test-regional-backend-service") >> mockGet
    mockGet.execute() >> expectedBackendService
    
    when:
    def result = handler.getRegionBackendServiceFromProvider(credentials, "us-central1", "test-regional-backend-service")
    
    then:
    result == expectedBackendService
    result.name == "test-regional-backend-service"
  }
  
  def "getRegionBackendServiceFromProvider propagates IOException"() {
    given:
    def mockCompute = Mock(Compute)
    def mockRegionBackendServices = Mock(Compute.RegionBackendServices)
    def mockGet = Mock(Compute.RegionBackendServices.Get)
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    mockCompute.regionBackendServices() >> mockRegionBackendServices
    mockRegionBackendServices.get("test-project", "us-central1", "test-regional-backend-service") >> mockGet
    mockGet.execute() >> { throw new IOException("Network error") }
    
    when:
    handler.getRegionBackendServiceFromProvider(credentials, "us-central1", "test-regional-backend-service")
    
    then:
    thrown(IOException)
  }

  def "getRegionBackendServicesToUpdate returns empty list when no internal load balancers"() {
    given:
    def handler = new BasicGoogleDeployHandler()
    def description = new BasicGoogleDeployDescription()
    def lbInfo = new BasicGoogleDeployHandler.LoadBalancerInfo()
    def policy = new GoogleHttpLoadBalancingPolicy()
    
    // Setup description with no internal load balancers
    description.instanceMetadata = [:]
    lbInfo.internalLoadBalancers = []
    lbInfo.internalHttpLoadBalancers = []
    
    when:
    def result = handler.getRegionBackendServicesToUpdate(description, "test-server-group", lbInfo, policy, "us-central1")
    
    then:
    result.isEmpty()
  }

  def "getBackendServiceToUpdate handles IOException by logging error and returning empty list"() {
    given:
    def mockDescription = Mock(BasicGoogleDeployDescription)
    def mockCompute = Mock(Compute)
    def mockBackendServices = Mock(Compute.BackendServices)
    def mockGet = Mock(Compute.BackendServices.Get)
    def mockUrlMaps = Mock(Compute.UrlMaps)
    def lbInfo = Mock(BasicGoogleDeployHandler.LoadBalancerInfo)
    def lbPolicy = new GoogleHttpLoadBalancingPolicy()
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    // Mock description
    mockDescription.getCredentials() >> credentials
    mockDescription.getInstanceMetadata() >> ["backend-service-names": "test-backend-service"]
    
    // Mock all required methods on LoadBalancerInfo
    lbInfo.getSslLoadBalancers() >> []
    lbInfo.getTcpLoadBalancers() >> []
    lbInfo.getHttpLoadBalancers() >> []
    lbInfo.getInternalLoadBalancers() >> []
    lbInfo.getInternalHttpLoadBalancers() >> []
    lbInfo.hasBackedServiceFromInput(mockDescription, lbPolicy) >> true
    
    // Mock compute API calls needed by GCEUtil.resolveHttpLoadBalancerNamesMetadata
    def mockUrlMapsList = Mock(Compute.UrlMaps.List)
    def mockGlobalForwardingRules = Mock(Compute.GlobalForwardingRules)
    def mockGlobalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)
    
    mockCompute.urlMaps() >> mockUrlMaps
    mockUrlMaps.list("test-project") >> mockUrlMapsList
    mockUrlMapsList.execute() >> [items: []]
    
    mockCompute.globalForwardingRules() >> mockGlobalForwardingRules
    mockGlobalForwardingRules.list("test-project") >> mockGlobalForwardingRulesList
    mockGlobalForwardingRulesList.execute() >> [items: []]
    
    // Mock backend service API to throw IOException
    mockCompute.backendServices() >> mockBackendServices
    mockBackendServices.get("test-project", "test-backend-service") >> mockGet
    mockGet.execute() >> { throw new IOException("GCP API error") }
    
    when:
    def result = handler.getBackendServiceToUpdate(mockDescription, "server-group", lbInfo, lbPolicy, "us-central1")
    
    then:
    // Should not throw exception, should return empty list instead
    result.isEmpty()
  }
  
  def "getBackendServiceToUpdate returns empty list when no backend services in metadata"() {
    given:
    def mockDescription = Mock(BasicGoogleDeployDescription)
    def lbInfo = Mock(BasicGoogleDeployHandler.LoadBalancerInfo)
    def lbPolicy = new GoogleHttpLoadBalancingPolicy()
    
    def handler = new BasicGoogleDeployHandler()
    
    // Mock description with no backend service metadata
    mockDescription.getInstanceMetadata() >> [:]
    
    // Mock all required methods on LoadBalancerInfo
    lbInfo.getSslLoadBalancers() >> []
    lbInfo.getTcpLoadBalancers() >> []
    lbInfo.getHttpLoadBalancers() >> []
    lbInfo.getInternalLoadBalancers() >> []
    lbInfo.getInternalHttpLoadBalancers() >> []
    lbInfo.hasBackedServiceFromInput(mockDescription, lbPolicy) >> false
    
    when:
    def result = handler.getBackendServiceToUpdate(mockDescription, "server-group", lbInfo, lbPolicy, "us-central1")
    
    then:
    result.isEmpty()
  }
  
  def "getRegionBackendServicesToUpdate handles IOException by logging error and returning empty list"() {
    given:
    def mockDescription = Mock(BasicGoogleDeployDescription)
    def mockCompute = Mock(Compute)
    def mockRegionBackendServices = Mock(Compute.RegionBackendServices)
    def mockGet = Mock(Compute.RegionBackendServices.Get)
    def lbInfo = Mock(BasicGoogleDeployHandler.LoadBalancerInfo)
    def lbPolicy = new GoogleHttpLoadBalancingPolicy()
    
    // Setup registry mocks for timeExecute
    def mockRegistry = Mock(Registry)
    def mockClock = Mock(Clock)
    def mockTimer = Mock(Timer)
    def mockId = Mock(Id)
    mockRegistry.clock() >> mockClock
    mockRegistry.createId(_, _) >> mockId
    mockId.withTags(_) >> mockId
    mockRegistry.timer(_) >> mockTimer
    mockClock.monotonicTime() >> 1000L
    
    def handler = new BasicGoogleDeployHandler(registry: mockRegistry)
    
    // Create real credentials with mocked compute
    def credentials = new GoogleNamedAccountCredentials.Builder()
        .name("test-account")
        .project("test-project")
        .compute(mockCompute)
        .build()
    
    // Mock description with proper instanceMetadata
    def instanceMetadata = [:]
    mockDescription.getCredentials() >> credentials
    mockDescription.getInstanceMetadata() >> instanceMetadata
    
    // Mock loadbalancer info to return internal load balancers  
    def mockInternalLB = Mock(GoogleInternalLoadBalancer.View)
    def mockBackendService = Mock(GoogleBackendService)
    mockBackendService.getName() >> "test-regional-backend-service"
    mockInternalLB.getBackendService() >> mockBackendService
    lbInfo.getInternalLoadBalancers() >> [mockInternalLB]
    lbInfo.getInternalHttpLoadBalancers() >> []
    
    // Mock regional backend service API to throw IOException
    mockCompute.regionBackendServices() >> mockRegionBackendServices
    mockRegionBackendServices.get("test-project", "us-central1", "test-regional-backend-service") >> mockGet
    mockGet.execute() >> { throw new IOException("GCP API error") }
    
    when:
    def result = handler.getRegionBackendServicesToUpdate(mockDescription, "server-group", lbInfo, lbPolicy, "us-central1")
    
    then:
    // Should not throw exception, should return empty list instead
    result.isEmpty()
  }
}
