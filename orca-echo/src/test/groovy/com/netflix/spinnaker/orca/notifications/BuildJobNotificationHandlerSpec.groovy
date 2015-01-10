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

package com.netflix.spinnaker.orca.notifications

import groovy.json.JsonSlurper
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.mayo.services.PipelineConfigurationService
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Shared
import spock.lang.Specification

class BuildJobNotificationHandlerSpec extends Specification {

  def pipeline1 = [
      name    : "pipeline1",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipeline2 = [
      name    : "pipeline2",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master1"]],
      stages  : [[type: "bake"],
                 [type: "deploy", cluster: [name: "foo"]]]
  ]

  def pipeline3 = [
      name    : "pipeline3",
      triggers: [[type  : "jenkins",
                  job   : "SPINNAKER-package-pond",
                  master: "master2"]],
      stages  : []
  ]

  @Shared DiscoveryClient discoveryClient = Stub(DiscoveryClient)

  void setup() {
    discoveryClient.getInstanceRemoteStatus() >> InstanceInfo.InstanceStatus.UP
  }

  void "should pick up stages subsequent to build job completing"() {
    setup:
    def pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(discoveryClient: discoveryClient, pipelineStarter: pipelineStarter, objectMapper: new OrcaObjectMapper())
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey(input.master, input.name)] = [pipeline1]

    when:
    handler.handle(input)

    then:
    1 * pipelineStarter.start(_) >> { json ->
      def config = new JsonSlurper().parseText(json) as Map
      assert config.stages.size() == 2
      assert config.stages[0].type == "bake"
      assert config.stages[1].type == "deploy"
      assert config.trigger.type == "jenkins"
      assert config.trigger.buildInfo == input
      def pipeline = new Pipeline()
      pipeline.id = "1"
      return pipeline
    }

    where:
    input = [name: "SPINNAKER-package-pond", master: "master1", lastBuildStatus: "Success"]
  }

  void "should add multiple pipeline targets to single trigger type"() {
    setup:
    def pipelineConfigService = Stub(PipelineConfigurationService) {
      getPipelines() >> [pipeline1, pipeline2]
    }
    def pipelineStarter = Stub(PipelineStarter)
    def handler = new BuildJobNotificationHandler(discoveryClient: discoveryClient, pipelineStarter: pipelineStarter, objectMapper: new OrcaObjectMapper(), pipelineConfigurationService: pipelineConfigService)

    when:
    handler.run()

    then:
    handler.interestingPipelines[key].name == ["pipeline1", "pipeline2"]

    where:
    key = "master1:SPINNAKER-package-pond"
  }

  void "should only trigger targets from the same master "() {
    given:
    def pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(discoveryClient: discoveryClient, pipelineStarter: pipelineStarter, objectMapper: new OrcaObjectMapper())
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey(input.master, input.name)] = [pipeline1]
    handler.interestingPipelines[BuildJobNotificationHandler.generateKey('master2', input.name)] = [pipeline3]

    expect:
    pipeline1.triggers.master != pipeline3.triggers.master

    when:
    handler.handle(input)

    then:
    1 * pipelineStarter.start({
      new JsonSlurper().parseText(it).trigger.master == master
    }) >> new Pipeline()

    where:
    master << ['master1', 'master2']
    input = [name: "SPINNAKER-package-pond", master: master, lastBuildStatus: "Success"]

  }

}
