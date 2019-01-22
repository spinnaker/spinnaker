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

package com.netflix.spinnaker.clouddriver.google.deploy

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpRequest
import com.google.api.client.http.HttpRequestFactory
import com.google.api.client.http.HttpResponse
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.compute.Compute
import com.google.api.services.compute.model.*
import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.GoogleExecutorTraits
import com.netflix.spinnaker.clouddriver.google.deploy.description.BasicGoogleDeployDescription
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleOperationException
import com.netflix.spinnaker.clouddriver.google.deploy.exception.GoogleResourceNotFoundException
import com.netflix.spinnaker.clouddriver.google.model.GoogleAutoscalingPolicy
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.googlecommon.batch.GoogleBatchRequest
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class GCEUtilSpec extends Specification {
  class TestExecutor implements GoogleExecutorTraits {
    def Registry registry = new DefaultRegistry()
  }

  private static final PROJECT_NAME = "my-project"
  private static final REGION = "us-central1"
  private static final ZONE = "us-central1-f"
  private static final IMAGE_NAME = "some-image-name"
  private static final PHASE = "SOME-PHASE"
  private static final INSTANCE_LOCAL_NAME_1 = "some-instance-name-1"
  private static final INSTANCE_LOCAL_NAME_2 = "some-instance-name-2"
  private static final INSTANCE_URL_1 = "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/us-central1-b/instances/$INSTANCE_LOCAL_NAME_1"
  private static final INSTANCE_URL_2 = "https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/zones/us-central1-b/instances/$INSTANCE_LOCAL_NAME_2"
  private static final IMAGE_PROJECT_NAME = "some-image-project"
  private static final GOOGLE_APPLICATION_NAME = "test"
  private static final BASE_IMAGE_PROJECTS = ["centos-cloud", "ubuntu-os-cloud"]

  def executor = new TestExecutor()

  @Shared
  def taskMock

  @Shared
  def safeRetry

  def setupSpec() {
    this.taskMock = Mock(Task)
    TaskRepository.threadLocalTask.set(taskMock)

    this.safeRetry = Stub(SafeRetry)
    safeRetry.doRetry(*_) >> { args ->
      def operation = args[0] as Closure
      return operation()
    }
  }

  void "query source images should succeed"() {
    setup:
      def executorMock = Mock(GoogleExecutorTraits)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
        new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def compute = new Compute.Builder(
        httpTransport, jsonFactory, httpRequestInitializer).setApplicationName(GOOGLE_APPLICATION_NAME).build()
      def soughtImage = new Image(name: IMAGE_NAME)
      def imageList = new ImageList(
        selfLink: "https://compute.googleapis.com/compute/alpha/projects/$PROJECT_NAME/global/images",
        items: [soughtImage]
      )

    when:
      def sourceImage = GCEUtil.queryImage(PROJECT_NAME, IMAGE_NAME, null, compute, taskMock, PHASE, GOOGLE_APPLICATION_NAME, BASE_IMAGE_PROJECTS, executorMock)

    then:
      1 * executorMock.timeExecuteBatch(_, "findImage", _) >> {
        GoogleBatchRequest batchRequest = it[0]
        assert batchRequest.queuedRequests.every { it.request != null }
        // 1 request for each of the 3 projects (PROJECT_NAME + BASE_IMAGE_PROJECTS)
        assert batchRequest.queuedRequests.size() == 3
        batchRequest.queuedRequests.each {
          it.callback.onSuccess(imageList, null)
        }
      }
      sourceImage == soughtImage
  }

  void "query source images should query configured imageProjects and succeed"() {
    setup:
      def executorMock = Mock(GoogleExecutorTraits)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
        new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def compute = new Compute.Builder(
        httpTransport, jsonFactory, httpRequestInitializer).setApplicationName(GOOGLE_APPLICATION_NAME).build()
      def credentials = new GoogleNamedAccountCredentials.Builder().compute(compute).imageProjects([IMAGE_PROJECT_NAME]).build()
      def soughtImage = new Image(name: IMAGE_NAME)
      def imageList = new ImageList(
        selfLink: "https://compute.googleapis.com/compute/alpha/projects/$PROJECT_NAME/global/images",
        items: [soughtImage]
      )

    when:
      def sourceImage = GCEUtil.queryImage(PROJECT_NAME, IMAGE_NAME, credentials, compute, taskMock, PHASE, GOOGLE_APPLICATION_NAME, BASE_IMAGE_PROJECTS, executorMock)

    then:
      1 * executorMock.timeExecuteBatch(_, "findImage", _) >> {
        GoogleBatchRequest batchRequest = it[0]
        assert batchRequest.queuedRequests.every { it.request != null }
        // 1 request for each of the 4 projects (PROJECT_NAME + IMAGE_PROJECT_NAME + BASE_IMAGE_PROJECTS)
        assert batchRequest.queuedRequests.size() == 4
        batchRequest.queuedRequests.each {
          it.callback.onSuccess(imageList, null)
        }
      }
      sourceImage == soughtImage
  }

  void "query source images should fail"() {
    setup:
      def executorMock = Mock(GoogleExecutorTraits)

      def httpTransport = GoogleNetHttpTransport.newTrustedTransport()
      def jsonFactory = JacksonFactory.defaultInstance
      def httpRequestInitializer =
        new GoogleCredential.Builder().setTransport(httpTransport).setJsonFactory(jsonFactory).build()
      def compute = new Compute.Builder(
        httpTransport, jsonFactory, httpRequestInitializer).setApplicationName(GOOGLE_APPLICATION_NAME).build()
      def emptyImageList = new ImageList()

    when:
      GCEUtil.queryImage(PROJECT_NAME, IMAGE_NAME, null, compute, taskMock, PHASE, GOOGLE_APPLICATION_NAME, BASE_IMAGE_PROJECTS, executorMock)

    then:
      1 * executorMock.timeExecuteBatch(_, "findImage", _) >> {
        GoogleBatchRequest batchRequest = it[0]
        assert batchRequest.queuedRequests.every { it.request != null }
        // 1 request for each of the 3 projects (PROJECT_NAME + IMAGE_PROJECT_NAME + BASE_IMAGE_PROJECTS)
        assert batchRequest.queuedRequests.size() == 3
        batchRequest.queuedRequests.each {
          it.callback.onSuccess(emptyImageList, null)
        }
      }
      thrown GoogleResourceNotFoundException
  }

  void "getImageFromArtifact"() {
    setup:
    def returnImage
    def executorMock = Mock(GoogleExecutorTraits)

    def compute = GroovyMock(Compute)
    compute.getRequestFactory() >> {
      def requestFactory = GroovyMock(HttpRequestFactory)
      requestFactory.buildGetRequest(_) >> {
        def httpRequest = GroovyMock(HttpRequest)
        httpRequest.setParser(_) >> {
          return httpRequest
        }
        httpRequest.execute() >> {
          def response = GroovyMock(HttpResponse)
          response.parseAs(_) >> {
            return returnImage
          }
          response.asType(HttpResponse) >> {
            return response
          }
          return response
        }
        return httpRequest
      }
      return requestFactory
    }

    def soughtImage = new Image(name: IMAGE_NAME)
    def artifact = Artifact.builder()
      .name(IMAGE_NAME)
      .reference("https://compute.googleapis.com/compute/v1/projects/$PROJECT_NAME/global/images/$IMAGE_NAME")
      .type("gce/image")
      .build()

    when:
    returnImage = soughtImage
    def sourceImage = GCEUtil.getImageFromArtifact(artifact, compute, taskMock, PHASE, safeRetry, executorMock)

    then:
    sourceImage == soughtImage

    when:
    artifact = Artifact.builder().type("github/file").build()
    returnImage = soughtImage
    GCEUtil.getImageFromArtifact(artifact, compute, taskMock, PHASE, safeRetry, executorMock)

    then:
    thrown GoogleOperationException
  }

  void "getBootImage"() {
    setup:
    def credentials = GroovyMock(GoogleNamedAccountCredentials)
    def compute = GroovyMock(Compute)
    credentials.compute >> compute
    credentials.project >> PROJECT_NAME
    def executor = GroovyMock(GoogleExecutorTraits)
    def artifact = new Artifact()
    GroovySpy(GCEUtil, global: true)

    when:
    def description = new BasicGoogleDeployDescription(credentials: credentials, image: IMAGE_NAME)
    GCEUtil.getBootImage(description,
                         taskMock,
                         PHASE,
                         GOOGLE_APPLICATION_NAME,
                         [IMAGE_PROJECT_NAME],
                         safeRetry,
                         executor)

    then:
    1 * GCEUtil.queryImage(PROJECT_NAME,
                           IMAGE_NAME,
                           credentials,
                           compute,
                           taskMock,
                           PHASE,
                           GOOGLE_APPLICATION_NAME,
                           [IMAGE_PROJECT_NAME],
                           executor) >> { return new Image() }
    0 * GCEUtil.getImageFromArtifact(*_)

    when:
    description = new BasicGoogleDeployDescription(credentials: credentials,
                                                   image: IMAGE_NAME,
                                                   imageArtifact: artifact,
                                                   imageSource: "ARTIFACT")
    GCEUtil.getBootImage(description,
                         taskMock,
                         PHASE,
                         GOOGLE_APPLICATION_NAME,
                         [IMAGE_PROJECT_NAME],
                         safeRetry,
                         executor)

    then:
    0 * GCEUtil.queryImage(*_)
    1 * GCEUtil.getImageFromArtifact(artifact, compute, taskMock, PHASE, safeRetry, executor) >> { return new Image() }
  }

  void "instance metadata with zero key-value pairs roundtrips properly"() {
    setup:
      def instanceMetadata = [:]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "instance metadata with exactly one key-value pair roundtrips properly"() {
    setup:
      def instanceMetadata = [someTestKey: "someTestValue"]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "instance metadata with more than one key-value pair roundtrips properly"() {
    setup:
      def instanceMetadata = [keyA: "valueA", keyB: "valueB", keyC: "valueC"]

    when:
      def computeMetadata = GCEUtil.buildMetadataFromMap(instanceMetadata)
      def roundtrippedMetadata = GCEUtil.buildMapFromMetadata(computeMetadata)

    then:
      roundtrippedMetadata == instanceMetadata
  }

  void "queryInstanceUrls should return matching instances from one zone"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/asia-east1-a": new InstancesScopedList(),
        "zones/asia-east1-b": new InstancesScopedList(),
        "zones/asia-east1-c": new InstancesScopedList(),
        "zones/europe-west1-b": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/us-central1-a": new InstancesScopedList(),
        "zones/us-central1-b": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_1, selfLink: INSTANCE_URL_1),
                                                                   new Instance(name: INSTANCE_LOCAL_NAME_2, selfLink: INSTANCE_URL_2)]),
        "zones/us-central1-c": new InstancesScopedList(),
        "zones/us-central1-f": new InstancesScopedList()
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)

    when:
      def instanceUrls =
        GCEUtil.queryInstanceUrls(PROJECT_NAME, REGION, ["some-instance-name-1", "some-instance-name-2"], computeMock, taskMock, PHASE, executor)
    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList
      instanceUrls == [INSTANCE_URL_1, INSTANCE_URL_2]
  }

  void "queryInstanceUrls should return matching instances from two zones"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/asia-east1-a": new InstancesScopedList(),
        "zones/asia-east1-b": new InstancesScopedList(),
        "zones/asia-east1-c": new InstancesScopedList(),
        "zones/europe-west1-b": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/us-central1-a": new InstancesScopedList(),
        "zones/us-central1-b": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_1, selfLink: INSTANCE_URL_1)]),
        "zones/us-central1-c": new InstancesScopedList(),
        "zones/us-central1-f": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_2, selfLink: INSTANCE_URL_2)])
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)

    when:
      def instanceUrls =
        GCEUtil.queryInstanceUrls(PROJECT_NAME, REGION, ["some-instance-name-1", "some-instance-name-2"], computeMock, taskMock, PHASE, executor)

    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList
      instanceUrls == [INSTANCE_URL_1, INSTANCE_URL_2]
  }

  void "queryInstanceUrls should throw exception when instance cannot be found"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/asia-east1-a": new InstancesScopedList(),
        "zones/asia-east1-b": new InstancesScopedList(),
        "zones/asia-east1-c": new InstancesScopedList(),
        "zones/europe-west1-b": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/us-central1-a": new InstancesScopedList(),
        "zones/us-central1-b": new InstancesScopedList(),
        "zones/us-central1-c": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_1, selfLink: INSTANCE_URL_1)]),
        "zones/us-central1-f": new InstancesScopedList()
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)

    when:
      GCEUtil.queryInstanceUrls(PROJECT_NAME, REGION, ["some-instance-name-1", "some-instance-name-2"], computeMock, taskMock, PHASE, executor)

    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      def exc = thrown GoogleResourceNotFoundException
      exc.message == "Instances [$INSTANCE_LOCAL_NAME_2] not found."
  }

  void "queryInstanceUrls should throw exception when instance is found but in a different region"() {
    setup:
      def computeMock = Mock(Compute)
      def instancesMock = Mock(Compute.Instances)
      def instancesAggregatedListMock = Mock(Compute.Instances.AggregatedList)
      def zoneToInstancesMap = [
        "zones/asia-east1-a": new InstancesScopedList(),
        "zones/asia-east1-b": new InstancesScopedList(),
        "zones/asia-east1-c": new InstancesScopedList(),
        "zones/europe-west1-b": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_1, selfLink: INSTANCE_URL_1)]),
        "zones/europe-west1-c": new InstancesScopedList(),
        "zones/europe-west1-c": new InstancesScopedList(instances: [new Instance(name: INSTANCE_LOCAL_NAME_2, selfLink: INSTANCE_URL_2)]),
        "zones/us-central1-a": new InstancesScopedList(),
        "zones/us-central1-b": new InstancesScopedList(),
        "zones/us-central1-c": new InstancesScopedList(),
        "zones/us-central1-f": new InstancesScopedList()
      ]
      def instanceAggregatedList = new InstanceAggregatedList(items: zoneToInstancesMap)

    when:
      GCEUtil.queryInstanceUrls(PROJECT_NAME, REGION, ["some-instance-name-1", "some-instance-name-2"], computeMock, taskMock, PHASE, executor)

    then:
      1 * computeMock.instances() >> instancesMock
      1 * instancesMock.aggregatedList(PROJECT_NAME) >> instancesAggregatedListMock
      1 * instancesAggregatedListMock.execute() >> instanceAggregatedList

      def exc = thrown GoogleResourceNotFoundException
      exc.message == "Instances [$INSTANCE_LOCAL_NAME_1, $INSTANCE_LOCAL_NAME_2] not found."
  }

  @Unroll
  void "buildServiceAccount should return an empty list when either email or authScopes are unspecified"() {
    expect:
      GCEUtil.buildServiceAccount(serviceAccountEmail, authScopes) == []

    where:
      serviceAccountEmail                      | authScopes
      null                                     | ["some-scope"]
      ""                                       | ["some-scope"]
      "something@test.iam.gserviceaccount.com" | null
      "something@test.iam.gserviceaccount.com" | []
  }

  @Unroll
  void "buildServiceAccount should prepend base url if necessary"() {
    expect:
      GCEUtil.buildServiceAccount("default", authScopes) == expectedServiceAccount

    where:
      authScopes                                                   || expectedServiceAccount
      ["cloud-platform"]                                           || [new ServiceAccount(email: "default", scopes: ["https://www.googleapis.com/auth/cloud-platform"])]
      ["devstorage.read_only"]                                     || [new ServiceAccount(email: "default", scopes: ["https://www.googleapis.com/auth/devstorage.read_only"])]
      ["https://www.googleapis.com/auth/logging.write", "compute"] || [new ServiceAccount(email: "default", scopes: ["https://www.googleapis.com/auth/logging.write", "https://www.googleapis.com/auth/compute"])]
  }

  @Unroll
  void "calibrateTargetSizeWithAutoscaler should adjust target size to within autoscaler min/max range if necessary"() {
    when:
      def autoscalingPolicy = new GoogleAutoscalingPolicy(minNumReplicas: minNumReplicas, maxNumReplicas: maxNumReplicas)
      def description = new BasicGoogleDeployDescription(targetSize: origTargetSize, autoscalingPolicy: autoscalingPolicy)
      GCEUtil.calibrateTargetSizeWithAutoscaler(description)

    then:
      description.targetSize == expectedTargetSize

    where:
      origTargetSize | minNumReplicas | maxNumReplicas | expectedTargetSize
      3              | 5              | 7              | 5
      9              | 5              | 7              | 7
      6              | 5              | 7              | 6
  }

  @Unroll
  void "checkAllForwardingRulesExist should fail if any loadbalancers aren't found"() {
    setup:
      def application = "my-application"
      def task = Mock(Task)
      def phase = "BASE_PHASE"
      // Note: the findAll lets us use the @Unroll feature to make this test more compact.
      def forwardingRuleNames = [networkLB?.name, httpLB?.name].findAll { it != null }
      def loadBalancerProvider = Mock(GoogleLoadBalancerProvider)
      def loadBalancers = [networkLB, httpLB].findAll { it != null }
      def notFoundNames = ['bogus-name']
      loadBalancerProvider.getApplicationLoadBalancers("") >> loadBalancers

    when:
      def foundLoadBalancers = GCEUtil.queryAllLoadBalancers(loadBalancerProvider, forwardingRuleNames, task, phase)

    then:
      foundLoadBalancers.collect { it.name } == forwardingRuleNames

    when:
      foundLoadBalancers = GCEUtil.queryAllLoadBalancers(loadBalancerProvider, forwardingRuleNames + 'bogus-name', task, phase)

    then:
      def resourceNotFound = thrown(GoogleResourceNotFoundException)
      def msg = "Load balancers $notFoundNames not found."
      resourceNotFound.message == msg.toString()

    where:
      networkLB                                                | httpLB
      new GoogleNetworkLoadBalancer(name: "network").getView() | new GoogleHttpLoadBalancer(name: "http").getView()
      null                                                     | new GoogleHttpLoadBalancer(name: "http").getView()
      new GoogleNetworkLoadBalancer(name: "network").getView() | null
      null                                                     | null
  }

  @Unroll
  void "should add http load balancer backend if metadata exists"() {
    setup:
      def loadBalancerNameList = lbNames
      def serverGroup =
        new GoogleServerGroup(
          name: 'application-derp-v000',
          region: REGION,
          regional: isRegional,
          zone: ZONE,
          asg: [
            (GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES): loadBalancerNameList,
          ],
          launchConfig: [
            instanceTemplate: new InstanceTemplate(name: "irrelevant-instance-template-name",
              properties: [
                'metadata': new Metadata(items: [
                  new Metadata.Items(
                    key: (GoogleServerGroup.View.LOAD_BALANCING_POLICY),
                    value: "{\"balancingMode\": \"UTILIZATION\",\"maxUtilization\": 0.80, \"namedPorts\": [{\"name\": \"http\", \"port\": 8080}], \"capacityScaler\": 0.77}"
                  ),
                  new Metadata.Items(
                    key: (GoogleServerGroup.View.BACKEND_SERVICE_NAMES),
                    value: backendServiceNames
                  )
                ])
              ])
          ]).view
      def computeMock = Mock(Compute)
      def backendServicesMock = Mock(Compute.BackendServices)
      def backendSvcGetMock = Mock(Compute.BackendServices.Get)
      def backendUpdateMock = Mock(Compute.BackendServices.Update)
      def googleLoadBalancerProviderMock = Mock(GoogleLoadBalancerProvider)

      def forwardingRules = Mock(Compute.ForwardingRules)
      def forwardingRulesList = Mock(Compute.ForwardingRules.List)
      def globalForwardingRules = Mock(Compute.GlobalForwardingRules)
      def globalForwardingRulesList = Mock(Compute.GlobalForwardingRules.List)

      googleLoadBalancerProviderMock.getApplicationLoadBalancers("") >> loadBalancerList
      def task = Mock(Task)
      def bs = new BackendService(backends: [])
      if (lbNames) {
        serverGroup.launchConfig.instanceTemplate.properties.metadata.items.add(
          new Metadata.Items(
            key: (GoogleServerGroup.View.GLOBAL_LOAD_BALANCER_NAMES),
            value: lbNames.join(",").trim()
          )
        )
      }

    when:
      GCEUtil.addHttpLoadBalancerBackends(computeMock, new ObjectMapper(), PROJECT_NAME, serverGroup, googleLoadBalancerProviderMock, task, "PHASE", executor)

    then:
      _ * computeMock.backendServices() >> backendServicesMock
      _ * backendServicesMock.get(PROJECT_NAME, 'backend-service') >> backendSvcGetMock
      _ * backendSvcGetMock.execute() >> bs
      _ * backendServicesMock.update(PROJECT_NAME, 'backend-service', bs) >> backendUpdateMock
      _ * backendUpdateMock.execute()

      _ * computeMock.globalForwardingRules() >> globalForwardingRules
      _ * globalForwardingRules.list(PROJECT_NAME) >> globalForwardingRulesList
      _ * globalForwardingRulesList.execute() >> new ForwardingRuleList(items: [])

      _ * computeMock.forwardingRules() >> forwardingRules
      _ * forwardingRules.list(PROJECT_NAME, _) >> forwardingRulesList
      _ * forwardingRulesList.execute() >> new ForwardingRuleList(items: [])
      bs.backends.size == lbNames.size

    where:
      isRegional | location | loadBalancerList                                                         | lbNames                          | backendServiceNames
      false      | ZONE     |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer'] | 'backend-service'
      true       | REGION   |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer'] | 'backend-service'
      false      | ZONE     |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer'] | 'backend-service'
      true       | REGION   |  [new GoogleHttpLoadBalancer(name: 'spinnaker-http-load-balancer').view] | ['spinnaker-http-load-balancer'] | 'backend-service'
      false      | ZONE     |  []                                                                      | []                               | null
      true       | REGION   |  []                                                                      | []                               | null
  }

  @Unroll
  void "should derive project id from #fullResourceLink"() {
    expect:
      GCEUtil.deriveProjectId(fullResourceLink) == "my-test-project"

    where:
      fullResourceLink << [
        "https://compute.googleapis.com/compute/v1/projects/my-test-project/global/firewalls/name-a",
        "compute.googleapis.com/compute/v1/projects/my-test-project/global/firewalls/name-a",
        "compute/v1/projects/my-test-project/global/firewalls/name-a",
        "projects/my-test-project/global/firewalls/name-a"
      ]
  }

  @Unroll
  void "should not derive project id from #fullResourceLink"() {
    when:
      GCEUtil.deriveProjectId(fullResourceLink)

    then:
      thrown IllegalArgumentException

    where:
      fullResourceLink << [
        null,
        "",
        "https://compute.googleapis.com/compute/v1/my-test-project/global/firewalls/name-a"
      ]
  }
}
