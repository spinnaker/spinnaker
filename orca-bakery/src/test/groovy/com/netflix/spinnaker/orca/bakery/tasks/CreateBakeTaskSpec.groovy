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

package com.netflix.spinnaker.orca.bakery.tasks

import com.netflix.spinnaker.orca.bakery.api.BakeRequest
import com.netflix.spinnaker.orca.bakery.api.BakeStatus
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import rx.Observable
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.bakery.api.BakeStatus.State.RUNNING
import static java.util.UUID.randomUUID

class CreateBakeTaskSpec extends Specification {

  @Subject task = new CreateBakeTask()
  Stage stage
  def mapper = new OrcaObjectMapper()
  def runningStatus = new BakeStatus(id: randomUUID(), state: RUNNING)

  @Shared Pipeline pipeline = new Pipeline()

  def bakeConfig = [
    region   : "us-west-1",
    package  : "hodor",
    user     : "bran",
    baseOs   : BakeRequest.OperatingSystem.ubuntu.name(),
    baseLabel: BakeRequest.Label.release.name()
  ]

  def setup() {
    task.mapper = mapper

    stage = new PipelineStage(pipeline, "bake", bakeConfig).asImmutable()
  }

  def "creates a bake for the correct region"() {
    given:
    task.bakery = Mock(BakeryService)

    when:
    task.execute(stage)

    then:
    1 * task.bakery.createBake(bakeConfig.region, _ as BakeRequest) >> Observable.from(runningStatus)
  }

  def "gets bake configuration from job context"() {
    given:
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(stage)

    then:
    bake.user == bakeConfig.user
    bake.packageName == bakeConfig.package
    bake.baseOs.name() == bakeConfig.baseOs
    bake.baseLabel.name() == bakeConfig.baseLabel
  }

  def "finds package details from the pipeline trigger"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo:[lastBuild:[artifacts:[
            [fileName: 'hodor_1.0_all.deb'],
            [fileName: 'hodor-1.0.noarch.rpm']
    ]]]]).build()
    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()
    def bake
    task.bakery = Mock(BakeryService) {
      1 * createBake(*_) >> {
        bake = it[1]
        Observable.from(runningStatus)
      }
    }

    when:
    task.execute(stage)

    then:
    bake.packageName == 'hodor_1.0_all'
  }

  def "fails if pipeline trigger includes artifacts but no artifact for the bake package"() {
    given:
    Pipeline pipelineWithTrigger = new Pipeline.Builder().withTrigger([buildInfo:[lastBuild:[artifacts:[
      [fileName: 'hodorhooodor_1.0_all.deb'],
      [fileName: 'hodor-1.0.noarch.rpm']
    ]]]]).build()
    Stage stage = new PipelineStage(pipelineWithTrigger, "bake", bakeConfig).asImmutable()

    when:
    task.execute(stage)

    then:
    IllegalStateException ise = thrown(IllegalStateException)
    ise.message.startsWith("Unable to find deployable artifact starting with hodor_ and ending with .deb in")
  }

  def "outputs the status of the bake"() {
    given:
    task.bakery = Stub(BakeryService) {
      createBake(*_) >> Observable.from(runningStatus)
    }

    when:
    def result = task.execute(stage)

    then:
    with(result.outputs.status) {
      id == runningStatus.id
      state == runningStatus.state
    }
  }

}
