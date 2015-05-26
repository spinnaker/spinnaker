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


package com.netflix.spinnaker.kato.aws.deploy.ops.discovery

import com.amazonaws.services.autoscaling.model.AutoScalingGroup
import com.amazonaws.services.autoscaling.model.Instance
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.DescribeInstancesResult
import com.amazonaws.services.ec2.model.Reservation
import com.netflix.amazoncomponents.security.AmazonClientProvider
import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.aws.services.AsgService
import com.netflix.spinnaker.kato.aws.services.RegionScopedProviderFactory
import com.netflix.spinnaker.kato.data.task.DefaultTaskStatus
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskState
import retrofit.RetrofitError
import retrofit.client.Response
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class DiscoverySupportUnitSpec extends Specification {
  def eureka = Mock(Eureka)

  @Subject
  def discoverySupport = new DiscoverySupport() {
    @Override
    protected long getDiscoveryRetryMs() {
      return 0
    }

    @Override
    boolean verifyInstanceAndAsgExist(AmazonEC2 amazonEC2, AsgService asgService, String instanceId, String asgName) {
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
  }

  void "should fail if discovery is not enabled"() {
    given:
    def description = new EnableDisableInstanceDiscoveryDescription(credentials: TestCredential.named('test'))

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, null, null, null, null)

    then:
    thrown(DiscoverySupport.DiscoveryNotConfiguredException)
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
      description, task, "phase", DiscoverySupport.DiscoveryStatus.Disable, instances
    )

    then:
    thrown(DiscoverySupport.RetryableException)
    discoverySupport.discoveryRetry * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * eureka.updateInstanceStatus(*_)
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
    (instanceIds.size() + 1) * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >>
      [
        instance: [
          app: appName
        ]
      ]

    0 * task.fail()
    instanceIds.each {
      1 * eureka.updateInstanceStatus(appName, it, discoveryStatus.value)
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123", "i-456"]
  }

  void "should retry on http errors from discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then: "should retry on NOT_FOUND"
    3 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    2 * eureka.getInstanceInfo(_) >> {
      throw failure
    } >>
      [
        instance: [
          app: appName
        ]
      ]
    instanceIds.each {
      1 * eureka.updateInstanceStatus(appName, it, discoveryStatus.value)
    }

    where:
    failure << [httpError(404), httpError(503)]

    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123"]

  }

  void "should retry on NOT_FOUND from discovery up to DISCOVERY_RETRY_MAX times"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      region: 'us-west-1',
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", discoveryStatus, instanceIds)

    then: "should only retry a maximum of DISCOVERY_RETRY_MAX times on NOT_FOUND"
    discoverySupport.discoveryRetry * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    discoverySupport.discoveryRetry * eureka.getInstanceInfo(_) >> {
      throw httpError(404)
    }
    0 * task.fail()
    thrown(RetrofitError)

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123"]
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
    (instanceIds.size() + 1) * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    1 * eureka.getInstanceInfo(_) >>
      [
        instance: [
          app: appName
        ]
      ]
    1 * task.fail()
    instanceIds.eachWithIndex { it, idx ->
      1 * eureka.updateInstanceStatus(appName, it, discoveryStatus.value) >> {
        if (!result[idx]) {
          throw new RuntimeException("blammo")
        }
        return null
      }
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123", "i-345", "i-456"]
    result = [true, false, true]
  }

  @Unroll
  void "should fail verification if asg does not exist"() {
    given:
    def discoverySupport = new DiscoverySupport()
    def amazonEC2 = Mock(AmazonEC2) {
      _ * describeInstances(_) >> {
        return new DescribeInstancesResult().withReservations(
          new Reservation().withInstances(instanceIds.collect {
            new com.amazonaws.services.ec2.model.Instance().withInstanceId(it)
          })
        )
      }
    }
    def asgService = Mock(AsgService) {
      1 * getAutoScalingGroup(_) >> autoScalingGroup
    }

    expect:
    discoverySupport.verifyInstanceAndAsgExist(amazonEC2, asgService, "i-12345", "asgName") == isVerified

    where:
    autoScalingGroup         | instanceIds || isVerified
    bASG()                   | []          || false
    bASG("Deleting")         | []          || false
    bASG(null, "---")        | []          || false
    null                     | []          || false
    bASG(null, "i-12345")    | ["---"]     || false
    bASG(null, "---")        | ["i-12345"] || false
    bASG(null, "i-12345", 0) | ["i-12345"] || false
    bASG(null, "i-12345")    | ["i-12345"] || true
  }

  private static RetrofitError httpError(int code) {
    RetrofitError.httpError('http://foo', new Response('http://foo', code, 'testing', [], null), null, Map)
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
}
