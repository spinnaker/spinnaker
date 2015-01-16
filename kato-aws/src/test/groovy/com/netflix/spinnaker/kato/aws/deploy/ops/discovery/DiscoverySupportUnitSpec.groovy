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
import com.netflix.spinnaker.kato.data.task.Task
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class DiscoverySupportUnitSpec extends Specification {
  @Subject
  def discoverySupport = new DiscoverySupport(restTemplate: Mock(RestTemplate))

  void "should fail if discovery is not enabled"() {
    given:
    def description = new EnableDisableInstanceDiscoveryDescription(credentials: TestCredential.named('test'))

    when:
    discoverySupport.updateDiscoveryStatusForInstances(description, null, null, null, null, null, null)

    then:
    thrown(DiscoverySupport.DiscoveryNotConfiguredException)
  }

  void "should fail task if application name is not derivable from asg name"() {
    given:
    def task = Mock(Task)
    def description = new EnableDisableInstanceDiscoveryDescription(
        credentials: TestCredential.named('test', [discovery: 'http://%s.discovery.netflix.net'])
    )
    def badAsgName = null

    when:
    discoverySupport.updateDiscoveryStatusForInstances(
        description, task, "phase", "us-west-1", DiscoverySupport.DiscoveryStatus.Disable, badAsgName, []
    )

    then:
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
        description, task, "PHASE", region, discoveryStatus, asgName, instanceIds
    )

    then:
    0 * task.fail()
    instanceIds.each {
      1 * discoverySupport.restTemplate.put("${discoveryUrl}/v2/apps/${asgName}/${it}/status?value=${discoveryStatus.value}", [:])
    }

    where:
    discoveryUrl = "http://us-west-1.discovery.netflix.net"
    region = "us-west-1"
    discoveryStatus = DiscoverySupport.DiscoveryStatus.Enable
    asgName = "kato"
    instanceIds = ["i-123", "i-456"]
  }
}
