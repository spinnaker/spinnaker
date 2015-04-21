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

import com.netflix.spinnaker.kato.aws.TestCredential
import com.netflix.spinnaker.kato.aws.deploy.description.EnableDisableInstanceDiscoveryDescription
import com.netflix.spinnaker.kato.data.task.DefaultTaskStatus
import com.netflix.spinnaker.kato.data.task.Task
import com.netflix.spinnaker.kato.data.task.TaskState
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class DiscoverySupportUnitSpec extends Specification {
  def mockRestTemplate = Mock(RestTemplate)

  @Subject
  def discoverySupport = new DiscoverySupport() {
    {
      this.restTemplate = mockRestTemplate
    }

    @Override
    protected long getDiscoveryRetryMs() {
      return 0
    }
  }

  void "should fail if discovery is not enabled"() {
    given:
    def description = new EnableDisableInstanceDiscoveryDescription(credentials: TestCredential.named('test'))

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, null, null, null, null, null)

    then:
    thrown(DiscoverySupport.DiscoveryNotConfiguredException)
  }

  void "should fail task if application name is not derivable from existing instance in discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
        credentials: TestCredential.named('test', [discovery: 'http://%s.discovery.netflix.net'])
    )
    def instances = ["i-123456"]

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
        description, task, "phase", "us-west-1", DiscoverySupport.DiscoveryStatus.Disable, instances
    )

    then:
    1 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    1 * task.fail()
    0 * discoverySupport.restTemplate.put(_, _)
  }

  void "should enable each instance individually in discovery"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
        credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
        description, task, "PHASE", region, discoveryStatus, instanceIds
    )

    then:
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    instanceIds.each {
      1 * discoverySupport.restTemplate.getForEntity("${discoveryUrl}/v2/instances/${it}", Map) >> new ResponseEntity<Map>(
          [
              instance: [
                  app: appName
              ]
          ], HttpStatus.OK
      )
      1 * discoverySupport.restTemplate.put("${discoveryUrl}/v2/apps/${appName}/${it}/status?value=${discoveryStatus.value}", [:])
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123", "i-456"]
  }

  void "should retry on NOT_FOUND from discovery up to DISCOVERY_RETRY_MAX times"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
      credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", region, discoveryStatus, instanceIds)

    then: "should retry on NOT_FOUND"
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    instanceIds.each {
      1 * discoverySupport.restTemplate.getForEntity("${discoveryUrl}/v2/instances/${it}", Map) >> {
        throw new HttpClientErrorException(HttpStatus.NOT_FOUND)
      }
      1 * discoverySupport.restTemplate.getForEntity("${discoveryUrl}/v2/instances/${it}", Map) >> new ResponseEntity<Map>(
        [
          instance: [
            app: appName
          ]
        ], HttpStatus.OK
      )
      1 * discoverySupport.restTemplate.put("${discoveryUrl}/v2/apps/${appName}/${it}/status?value=${discoveryStatus.value}", [:])
    }

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", region, discoveryStatus, instanceIds)

    then: "should retry on SERVICE_UNAVAILABLE"
    2 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    instanceIds.each {
      1 * discoverySupport.restTemplate.getForEntity("${discoveryUrl}/v2/instances/${it}", Map) >> {
        throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
      }
      1 * discoverySupport.restTemplate.getForEntity("${discoveryUrl}/v2/instances/${it}", Map) >> new ResponseEntity<Map>(
        [
          instance: [
            app: appName
          ]
        ], HttpStatus.OK
      )
      1 * discoverySupport.restTemplate.put("${discoveryUrl}/v2/apps/${appName}/${it}/status?value=${discoveryStatus.value}", [:])
    }

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", region, discoveryStatus, instanceIds)

    then: "should only retry a maximum of DISCOVERY_RETRY_MAX times on NOT_FOUND"
    DiscoverySupport.DISCOVERY_RETRY_MAX * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    instanceIds.each {
      DiscoverySupport.DISCOVERY_RETRY_MAX * discoverySupport.restTemplate.getForEntity(*_) >> {
        throw new HttpClientErrorException(HttpStatus.NOT_FOUND)
      }
    }
    thrown(HttpClientErrorException)

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", region, discoveryStatus, instanceIds)

    then: "should never retry on BAD_REQUEST"
    1 * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED)
    0 * task.fail()
    instanceIds.each {
      1 * discoverySupport.restTemplate.getForEntity(*_) >> {
        throw new HttpClientErrorException(HttpStatus.BAD_REQUEST)
      }
    }
    thrown(HttpClientErrorException)

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    appName = "kato"
    instanceIds = ["i-123"]
  }


  void "should retry when encounters a ResourceAccessException"() {
    given:
    def task = Mock(Task)

    def description = new EnableDisableInstanceDiscoveryDescription(
     credentials: TestCredential.named('test', [discovery: discoveryUrl])
    )

    when:
        discoverySupport.updateDiscoveryStatusForInstances(description, task, "PHASE", region, discoveryStatus, instanceIds)

    then:
        DiscoverySupport.DISCOVERY_RETRY_MAX * task.getStatus() >> new DefaultTaskStatus(state: TaskState.STARTED);
        DiscoverySupport.DISCOVERY_RETRY_MAX * task.updateStatus(_, _) >> {throw new ResourceAccessException("msg")}
        thrown ResourceAccessException
    where:
        discoveryUrl = "http://us-west-1.discovery.netflix.net"
        region = "us-west-1"
        discoveryStatus = DiscoverySupport.DiscoveryStatus.Disable
        appName = "kato"
        instanceIds = ["i-123"]

  }
}
