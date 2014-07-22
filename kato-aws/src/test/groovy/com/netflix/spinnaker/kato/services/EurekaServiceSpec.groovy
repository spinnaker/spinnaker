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
package com.netflix.spinnaker.kato.services

import com.netflix.spinnaker.kato.data.task.DefaultTask
import org.springframework.web.client.RestTemplate
import spock.lang.Specification
import spock.lang.Subject

class EurekaServiceSpec extends Specification {

  def mockThrottleService = Mock(ThrottleService)
  def mockRestTemplate = Mock(RestTemplate)
  @Subject def eurekaService = new EurekaService(mockThrottleService, "http://%s.discovery.%s.netflix.net", mockRestTemplate,
      new DefaultTask("1"), "PHASE", "ENV", "us-west-1")

  void 'should enable instances for asg in Eureka'() {
    when:
    eurekaService.enableInstancesForAsg("asg1", ["i1", "i2"])

    then:
    1 * mockRestTemplate.put("http://us-west-1.discovery.ENV.netflix.net/eureka/v2/apps/asg1/i1/status?value=UP", [:])
    0 * _

    then:
    1 * mockThrottleService.sleepMillis(_)

    then:
    1 * mockRestTemplate.put("http://us-west-1.discovery.ENV.netflix.net/eureka/v2/apps/asg1/i2/status?value=UP", [:])

    and:
    eurekaService.task.history*.status == [
      "Attempting to enable instance 'i1'.",
      "Attempting to enable instance 'i2'."
    ]
  }

  void 'should disable instances for asg in Eureka'() {
    when:
    eurekaService.disableInstancesForAsg("asg1", ["i1", "i2"])

    then:
    1 * mockRestTemplate.put("http://us-west-1.discovery.ENV.netflix.net/eureka/v2/apps/asg1/i1/status?value=OUT_OF_SERVICE", [:])
    0 * _

    then:
    1 * mockThrottleService.sleepMillis(_)

    then:
    1 * mockRestTemplate.put("http://us-west-1.discovery.ENV.netflix.net/eureka/v2/apps/asg1/i2/status?value=OUT_OF_SERVICE", [:])

    and:
    eurekaService.task.history*.status == [
      "Attempting to disable instance 'i1'.",
      "Attempting to disable instance 'i2'."
    ]
  }

  void 'should do nothing without a discoveryHostFormat'() {
    @Subject def eurekaService = new EurekaService(mockThrottleService, null, mockRestTemplate,
      new DefaultTask("1"), "PHASE", "ENV", "us-west-1")

    when:
    eurekaService.enableInstancesForAsg("asg1", ["i1", "i2"])

    then:
    0 * _

    and:
    eurekaService.task.history*.status == []
  }

  void 'should log unknown application'() {
    when:
    eurekaService.enableInstancesForAsg("", ["i1", "i2"])

    then:
    eurekaService.task.history*.status == [
      "Could not derive application name from ASG name and unable to enable in Eureka!"
    ]

  }

  void 'should do nothing without instances'() {
    when:
    eurekaService.enableInstancesForAsg("asg1", [])

    then:
    0 * _

    and:
    eurekaService.task.history*.status == []
  }

}
