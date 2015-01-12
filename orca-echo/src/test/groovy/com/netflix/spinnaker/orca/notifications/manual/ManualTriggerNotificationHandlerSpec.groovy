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

package com.netflix.spinnaker.orca.notifications.manual

import groovy.json.JsonSlurper
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.orca.notifications.PipelineIndexer
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class ManualTriggerNotificationHandlerSpec extends Specification {

  @Shared
  def pipeline1 = [
      application: "app",
      name       : "pipeline1",
      triggers   : [[type  : "jenkins",
                     job   : "SPINNAKER-package-pond",
                     master: "master1"]],
      stages     : [[type: "bake"],
                    [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipelineIndexer = Stub(PipelineIndexer)
  def discoveryClient = Stub(DiscoveryClient)
  def pipelineStarter = Mock(PipelineStarter)
  @Subject def handler = new ManualTriggerNotificationHandler(pipelineIndexer)

  void setup() {
    handler.objectMapper = new ObjectMapper()
    handler.pipelineStarter = pipelineStarter
    handler.discoveryClient = discoveryClient

    discoveryClient.instanceRemoteStatus >> InstanceInfo.InstanceStatus.UP
  }

  void "should trigger pipelines from manual event"() {
    given:
    pipelineIndexer.getPipelines() >> ImmutableMap.of(
        new ManualTriggerNotificationHandler.PipelineId(app, pipeline1.name), [pipeline1]
    )

    when:
    handler.handle(application: app, name: pipeline1.name, user: user)

    then:
    1 * pipelineStarter.start(_) >> { json ->
      def config = new JsonSlurper().parseText(json) as Map
      assert config.stages.size() == 2
      assert config.stages[0].type == "bake"
      assert config.stages[1].type == "deploy"
      assert config.trigger.type == "manual"
      def pipeline = new Pipeline()
      pipeline.id = "1"
      return pipeline
    }

    where:
    app = pipeline1.application
    user = "danw@netflix.com"
  }
}
