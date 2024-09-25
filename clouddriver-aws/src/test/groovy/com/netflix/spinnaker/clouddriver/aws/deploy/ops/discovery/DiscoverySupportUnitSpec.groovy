/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.deploy.ops.discovery

import com.amazonaws.AmazonServiceException
import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.InstanceState
import com.amazonaws.services.ec2.model.InstanceStateName
import com.amazonaws.services.ec2.model.Reservation
import com.netflix.spinnaker.clouddriver.aws.TestCredential
import com.netflix.spinnaker.clouddriver.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider
import com.netflix.spinnaker.clouddriver.aws.services.AsgService
import com.netflix.spinnaker.clouddriver.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.clouddriver.data.task.DefaultTaskStatus
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskState
import com.netflix.spinnaker.clouddriver.eureka.api.Eureka
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.AbstractEurekaSupport
import com.netflix.spinnaker.clouddriver.eureka.deploy.ops.EurekaSupportConfigurationProperties
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DiscoverySupportUnitSpec extends Specification {
  def eureka = Mock(Eureka)

  @Subject
  def discoverySupport = new AwsEurekaSupport() {
    {
      clusterProviders = []
    }

    @Override
    protected long getDiscoveryRetryMs() {
      return 0
    }

    @Override
    boolean verifyInstanceAndAsgExist(def credentials, String region, String instanceId, String asgName) {
      return true
    }
  }

  void setup() {
    discoverySupport.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      getAmazonClientProvider() >> {
        return Stub(AmazonClientProvider)
      }

      forRegion(_, _) >> {
        return Stub(RegionScopedProviderFactory.RegionScopedProvider) {
          getEureka() >> eureka
        }
      }
    }
    discoverySupport.eurekaSupportConfigurationProperties = new EurekaSupportConfigurationProperties()
  }

  void "should fail if discovery is not enabled"() {
    given:
    def description = new EnableDisableInstanceDiscoveryDescription(credentials: TestCredential.named('test'))

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, null, null, null, null)

    then:
    thrown(AbstractEurekaSupport.DiscoveryNotConfiguredException)
  }

  void "should fail task if application name is not derivable from existing instance in discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-east-1',
      credentials: TestCredential.named('test', [discovery: 'http://{{region}}.discovery.netflix.net'])
    )
    def instances = ["i-123456"]

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
      description, task, "phase", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE, instances
    )

    then:
    thrown(AbstractEurekaSupport.RetryableException)
    discoverySupport.eurekaSupportConfigurationProperties.retryMax * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    0 * eureka.updateInstanceStatus(*_)
  }

  void "should short-circuit if application name is not in discovery and server group does not exist"() {
    given:
    def discoverySupport = new AwsEurekaSupport() {
      {
        clusterProviders = []
      }

      @Override
      protected long getDiscoveryRetryMs() {
        return 0
      }

      @Override
      boolean verifyInstanceAndAsgExist(def credentials, String region, String instanceId, String asgName) {
        return false
      }
    }
    discoverySupport.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      getAmazonClientProvider() >> {
        return Stub(AmazonClientProvider)
      }

      forRegion(_, _) >> {
        return Stub(RegionScopedProviderFactory.RegionScopedProvider) {
          getEureka() >> eureka
        }
      }
    }
    discoverySupport.eurekaSupportConfigurationProperties = new EurekaSupportConfigurationProperties()

    and:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      asgName: 'myapp-test-v000',
      region: 'us-east-1',
      credentials: TestCredential.named('test', [discovery: 'http://{{region}}.discovery.netflix.net'])
    )
    def instances = ["i-123456"]

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
      description, task, "phase", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE, instances
    )

    then:
    discoverySupport.eurekaSupportConfigurationProperties.retryMax * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    0 * eureka.updateInstanceStatus(*_)
    1 * task.updateStatus(_, "Could not find application name in Discovery or AWS, short-circuiting (asg: myapp-test-v000, region: us-east-1)")
  }

  void "should enable each instance individually in discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
      description, task, "PHASE", discoveryStatus, instanceIds
    )

    then:
    (instanceIds.size() + 1) * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >>
      [
        instance: [
          app: appName,
          status: "OUT_OF_SERVICE"
        ]
      ]

    0 * task.fail()
    instanceIds.each {
      1 * eureka.resetInstanceStatus(appName, it, AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> response(200)
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["i-123", "i-456"]
  }

  void "should fail but still try all instances before failing task"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds, true)

    then:
    task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * task.fail()
    1 * eureka.getInstanceInfo(_) >> [ instance: [ app: appName, status: "OUT_OF_SERVICE" ] ]
    1 * eureka.resetInstanceStatus(appName, "bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(400))}
    1 * eureka.resetInstanceStatus(appName, "good", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {response(200)}
    1 * eureka.resetInstanceStatus(appName, "also-bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(400))}
    1 * task.updateStatus("PHASE", { it.startsWith("Looking up discovery") })
    3 * task.updateStatus("PHASE", { it.startsWith("Attempting to mark") })
    2 * task.updateStatus('PHASE', { it.startsWith("Failed updating status") })
    1 * task.updateStatus("PHASE", { it.startsWith("Failed marking instances 'UP'")})
    0 * _

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["good", "bad", "also-bad"]
  }

  void "should succeed despite some failures due to targetHealthyDeployPercentage"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl]),
      targetHealthyDeployPercentage: 20
    )
    discoverySupport.eurekaSupportConfigurationProperties.retryMax = 1

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then:
    task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >> [ instance: [ app: appName, status: "OUT_OF_SERVICE" ] ]
    1 * eureka.resetInstanceStatus(appName, "bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(500))}
    1 * eureka.resetInstanceStatus(appName, "good", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> response(200)
    1 * eureka.resetInstanceStatus(appName, "also-bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(500))}
    1 * task.updateStatus("PHASE", { it.startsWith("Looking up discovery") })
    3 * task.updateStatus("PHASE", { it.startsWith("Attempting to mark") })
    0 * task.updateStatus("PHASE", { it.startsWith("Failed marking instances 'UP'")})
    2 * task.updateStatus('PHASE', { it.startsWith("Failed updating status")})
    1 * task.addResultObjects([['discoverySkippedInstanceIds':['bad', 'also-bad']]])
    0 * task.fail()
    0 * _

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["good", "bad", "also-bad"]

  }

  void "should fail despite targetHealthyDeployPercentage=50"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl]),
      targetHealthyDeployPercentage: 50
    )
    discoverySupport.eurekaSupportConfigurationProperties.retryMax = 1

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds, true)

    then:
    task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >> [ instance: [ app: appName, status: "OUT_OF_SERVICE" ] ]
    1 * eureka.resetInstanceStatus(appName, "bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(500))}
    1 * eureka.resetInstanceStatus(appName, "good", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> response(200)
    1 * eureka.resetInstanceStatus(appName, "also-bad", AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {throw new SpinnakerHttpException(httpError(500))}
    1 * task.updateStatus("PHASE", { it.startsWith("Looking up discovery") })
    3 * task.updateStatus("PHASE", { it.startsWith("Attempting to mark") })
    1 * task.updateStatus("PHASE", { it.startsWith("Failed marking instances 'UP'")})
    2 * task.updateStatus('PHASE', { it.startsWith("Failed updating status of")})
    1 * task.fail()
    0 * _

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["good", "bad", "also-bad"]
  }


  @Unroll
  void "should retry on NOT_FOUND from getInstanceInfo up to DISCOVERY_RETRY_MAX times"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then: "should only retry a maximum of DISCOVERY_RETRY_MAX times on NOT_FOUND"
    discoverySupport.eurekaSupportConfigurationProperties.retryMax * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    discoverySupport.eurekaSupportConfigurationProperties.retryMax * eureka.getInstanceInfo(_) >> {
        throw new SpinnakerHttpException(httpError(errorCode))
    }
    0 * task.fail()
    thrown(SpinnakerHttpException)

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["i-123"]
    errorCode << [404, 406, 503]
  }

  void "should retry on non 200 response from discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then: "should only retry a maximum of DISCOVERY_RETRY_MAX times on NOT_FOUND"
    1 * eureka.getInstanceInfo('i-123') >>
      [
        instance: [
          app: appName,
          status: "OUT_OF_SERVICE"
        ]
      ]
    3 * eureka.resetInstanceStatus(appName, 'i-123', AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >>> [response(302), response(201), response(200)]
    4 * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    0 * task.fail()

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["i-123"]
  }

  @Unroll
  void "should NOT fails if strict=#strict for #status operation if instance is not found"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-east-1',
      credentials: TestCredential.named('test', [discovery: "http://us-east-1.discovery.netflix.net"])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", status, ['i-123'])

    then: "task should not be failed"
    1 * eureka.getInstanceInfo('i-123') >>
      [
        instance: [
          app: appName
        ]
      ]
    eureka.updateInstanceStatus(appName, 'i-123', status.value) >> { throw new SpinnakerHttpException(httpError(404)) }
    eureka.resetInstanceStatus(appName, 'i-123', AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> { throw new SpinnakerHttpException(httpError(404)) }
    task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    0 * task.fail()

    where:
    appName = "kato"
    status                                                | strict
    AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE  | false
    AbstractEurekaSupport.DiscoveryStatus.UP              | false
    AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE  | true
    AbstractEurekaSupport.DiscoveryStatus.UP              | true
  }

  void "should attempt to mark each instance in discovery even if some fail"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then: "should retry on NOT_FOUND"
    (instanceIds.size() + 1) * task.getStatus() >> new DefaultTaskStatus(TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >>
      [
        instance: [
          app: appName,
          status: "OUT_OF_SERVICE"
        ]
      ]
    1 * task.fail()
    instanceIds.eachWithIndex { it, idx ->
      1 * eureka.resetInstanceStatus(appName, it, AbstractEurekaSupport.DiscoveryStatus.OUT_OF_SERVICE.value) >> {
        if (!result[idx]) {
          throw new RuntimeException("blammo")
        }
        return response(200)
      }
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = AbstractEurekaSupport.DiscoveryStatus.UP
    appName = "kato"
    instanceIds = ["i-123", "i-345", "i-456"]
    result = [true, false, true]
  }

  @Unroll
  void "should fail verification if asg does not exist"() {
    given:
    def discoverySupport = new AwsEurekaSupport()
    def amazonEC2 = Mock(AmazonEC2) {
      _ * describeInstances(_) >> {
        return new DescribeInstancesResult().withReservations(
          new Reservation().withInstances(instanceIds.collect {
            new com.amazonaws.services.ec2.model.Instance()
              .withInstanceId(it.name)
              .withState(new InstanceState().withName(it.state))
          })
        )
      }
    }
    def asgService = Mock(AsgService) {
      1 * getAutoScalingGroup(_) >> autoScalingGroup
    }

    discoverySupport.regionScopedProviderFactory = Stub(RegionScopedProviderFactory) {
      getAmazonClientProvider() >> {
        return Stub(AmazonClientProvider)
      }

      forRegion(_, _) >> {
        return Stub(RegionScopedProviderFactory.RegionScopedProvider) {
          getEureka() >> eureka
          getAmazonEC2() >> amazonEC2
          getAsgService() >> asgService
        }
      }
    }

    expect:
    discoverySupport.verifyInstanceAndAsgExist(TestCredential.named('test'), 'us-west-1' , "i-12345", "asgName") == isVerified

    where:
    autoScalingGroup         | instanceIds                                              || isVerified
    bASG()                   | []                                                       || false
    bASG("Deleting")         | []                                                       || false
    bASG(null, "---")        | []                                                       || false
    null                     | []                                                       || false
    bASG(null, "i-12345")    | [[name: "---", state: InstanceStateName.Terminated]]     || false
    bASG(null, "---")        | ["i-12345"]                                              || false
    bASG(null, "i-12345", 0) | ["i-12345"]                                              || false
    bASG(null, "i-12345")    | [[name: "i-12345", state: InstanceStateName.Running]]    || true
    bASG(null, "i-12345")    | [[name: "i-12345", state: InstanceStateName.Pending]]    || true
    bASG(null, "i-12345")    | [[name: "i-12345", state: InstanceStateName.Terminated]] || false

  }

  @Unroll
  void "should return Optional.empty() if target asg does not exist"() {
    expect:
    !AwsEurekaSupport.doesCachedClusterContainDiscoveryStatus(
      clusterProviders, account, region, asgName, "UP"
    ).present

    where:
    clusterProviders | account | region      | asgName
    []               | null    | null        | null
    [Mock(ClusterProvider) {
      1 * getServerGroup("test", "us-west-1", "asg") >> null
    }]               | "test"  | "us-west-1" | "asg"
  }

  @Unroll
  void "should return true if server group has at least one instance with desired discovery status"() {
    expect:
    AwsEurekaSupport.doesCachedClusterContainDiscoveryStatus(
      clusterProviders, account, region, asgName, "UP"
    ).get() == isPresent

    where:
    clusterProviders | account | region      | asgName | isPresent
    [Mock(ClusterProvider) {
      1 * getServerGroup("test", "us-west-1", "asg") >> buildServerGroup("UP", "OUT_OF_SERVICE")
    }]               | "test"  | "us-west-1" | "asg"   | true
    [Mock(ClusterProvider) {
      1 * getServerGroup("test", "us-west-1", "asg") >> buildServerGroup("OUT_OF_SERVICE")
    }]               | "test"  | "us-west-1" | "asg"   | false
  }

  private ServerGroup buildServerGroup(String ... eurekaStatuses) {
    return new DefaultServerGroup(
      instances: eurekaStatuses.collect { String eurekaStatus ->
        Mock(com.netflix.spinnaker.clouddriver.model.Instance) {
          1 * getHealth() >> {
            [
                [eurekaStatus: eurekaStatus]
            ]
          }
          0 * _
        }
      }
    )
  }

  private static RetrofitError httpError(int code) {
    RetrofitError.httpError('http://foo', response(code), null, Map)
  }

  private static Response response(int code) {
    new Response('http://foo', code, 'WAT', [], null)
  }

  private static AmazonServiceException amazonError(int code) {
    def ase = new AmazonServiceException("boom")
    ase.setStatusCode(code)
    return ase
  }

  private static bASG(String status = null, String instanceId = null, int capacity = 1) {
    def autoScalingGroup = new AutoScalingGroup()
    if (status) {
      autoScalingGroup = autoScalingGroup.withStatus(status)
    }
    if (instanceId) {
      autoScalingGroup = autoScalingGroup.withInstances(new Instance().withInstanceId(instanceId))
    }
    autoScalingGroup = autoScalingGroup.withDesiredCapacity(capacity)

    return autoScalingGroup
  }

  private static class DefaultServerGroup implements ServerGroup {
    String name
    String type
    String cloudProvider
    String region
    Boolean disabled
    Long createdTime
    Set<String> zones
    Set<com.netflix.spinnaker.clouddriver.model.Instance> instances
    Set<String> loadBalancers
    Set<String> securityGroups
    Map<String, Object> launchConfig
    ServerGroup.InstanceCounts instanceCounts
    ServerGroup.Capacity capacity
    ServerGroup.ImageSummary getImageSummary() {}
    ServerGroup.ImagesSummary getImagesSummary() {}

    Boolean isDisabled() {
      disabled
    }
  }
}
