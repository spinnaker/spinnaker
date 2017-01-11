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
import com.netflix.spinnaker.orca.clouddriver.config.CloudDriverConfiguration
import com.netflix.spinnaker.orca.config.JesqueConfiguration
import com.netflix.spinnaker.orca.pipeline.PipelineLauncher
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.explore.JobExplorer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.IgnoreIf
import spock.lang.Specification
import static com.netflix.spinnaker.orca.test.net.Network.notReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

@IgnoreIf({ notReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, CloudDriverConfiguration, BatchTestConfiguration,
                                 EmbeddedRedisConfiguration, JesqueConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeSpec extends Specification {

  @Autowired PipelineLauncher pipelineLauncher
  @Autowired ObjectMapper mapper
  @Autowired JobExplorer jobExplorer

  def "can bake and deploy"() {
    given:
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipeline = pipelineLauncher.start(configJson)
    def jobName = OrcaSmokeUtils.buildJobName(config.application, config.name, pipeline.id)

    then:
    jobExplorer.getJobInstanceCount(jobName) == 1

    def jobInstance = jobExplorer.getJobInstances(jobName, 0, 1)[0]
    def jobExecutions = jobExplorer.getJobExecutions(jobInstance)

    jobExecutions.size == 1

    with(jobExecutions[0]) {
      status == BatchStatus.COMPLETED
      exitStatus == ExitStatus.COMPLETED
    }

    where:
    app = "mimirdemo"
    region = "us-west-1"
    config = [
        application: app,
        name       : "my-pipeline",
        stages     : [
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
    ]
  }

  def "can deploy next ASG"() {
    def configJson = mapper.writeValueAsString(config)

    when:
    def pipeline = pipelineLauncher.start(configJson)
    def jobName = OrcaSmokeUtils.buildJobName(config.application, config.name, pipeline.id)

    then:
    jobExplorer.getJobInstanceCount(jobName) == 1

    def jobInstance = jobExplorer.getJobInstances(jobName, 0, 1)[0]
    def jobExecutions = jobExplorer.getJobExecutions(jobInstance)

    jobExecutions.size == 1

    with(jobExecutions[0]) {
      status == BatchStatus.COMPLETED
      exitStatus == ExitStatus.COMPLETED
    }

    where:
    config = [
        application: "mimirdemo",
        name       : "my-pipeline",
        stages     : [
            [
                type             : "copyLastAsg",
                application      : "mimirdemo",
                stack            : "test",
                availabilityZones: ["us-east-1": []],
                credentials      : "test"
            ]
        ]
    ]
  }
}

