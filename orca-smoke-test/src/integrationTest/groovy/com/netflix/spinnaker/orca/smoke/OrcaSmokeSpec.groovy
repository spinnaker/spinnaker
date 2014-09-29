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

package com.netflix.spinnaker.orca.smoke

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.kato.config.KatoConfiguration
import com.netflix.spinnaker.orca.oort.config.OortConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Requires
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.net.Network.isReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

@Requires({ isReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, KatoConfiguration, BatchTestConfiguration, OortConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeSpec extends Specification {

  @Autowired PipelineStarter jobStarter
  @Autowired ObjectMapper mapper

  def "can bake and deploy"() {
    given:
    def configJson = mapper.writeValueAsString(config)

    when:
    def execution = jobStarter.start(configJson)

    then:
    execution.status == BatchStatus.COMPLETED
    execution.exitStatus == ExitStatus.COMPLETED

    where:
    app = "mimirdemo"
    region = "us-west-1"
    config = [
      [
        type     : "bake",
        region   : region,
        user     : "danw",
        package  : app,
        baseOs   : "ubuntu",
        baseLabel: "release"
      ], [
        type             : "deploy",
        application      : app,
        stack            : "test",
        instanceType     : "m3.large",
        securityGroups   : ["nf-infrastructure-vpc", "nf-datacenter-vpc"],
        subnetType       : "internal",
        availabilityZones: [(region): []],
        capacity         : [min: 1, max: 1, desired: 1],
        credentials      : "test"
      ]
    ]
  }

  def "can deploy next ASG"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def execution = jobStarter.start(configJson)

    then:
    execution.status == BatchStatus.COMPLETED
    execution.exitStatus == ExitStatus.COMPLETED

    where:
    config = [
      [
        type             : "copyLastAsg",
        application      : "mimirdemo",
        stack            : "test",
        availabilityZones: ["us-east-1": []],
        credentials      : "test"
      ]
    ]
  }
}

