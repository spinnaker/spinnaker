/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.kato.pipeline

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.InheritConstructors
import org.springframework.batch.core.Step
import org.springframework.batch.core.StepExecutionListener
import spock.lang.Specification
import spock.lang.Subject

class QuickPatchStageSpec extends Specification {

  def oortHelper = Mock(OortHelper)
  def bulkQuickPatchStage = Mock(BulkQuickPatchStage)
  def step = Stub(Step)

  @Subject quickPatchStage = new NoBatchQuickPatchStage(oortHelper: oortHelper, bulkQuickPatchStage: bulkQuickPatchStage, step: step)

  def "no-ops if there are no instances"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "quickPatch", context)

    expect:
    !stage.initializationStage
    stage.status == ExecutionStatus.NOT_STARTED

    when:
    def steps = quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(_, null, true, false) >> [:]
    steps.isEmpty()
    stage.initializationStage
    stage.status == ExecutionStatus.SUCCEEDED
    0 * _

    where:
    context = [:]
  }
  def "configures bulk quickpatch"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "quickPatch", stageContext)

    when:
    def steps = quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(_, null, true, false) >> instances
    steps == [step]
    !stage.initializationStage
    stage.status == ExecutionStatus.NOT_STARTED
    stage.afterStages.size() == 1
    with(stage.afterStages[0]) {
      name == "bulkQuickPatchStage"
      stageBuilder == bulkQuickPatchStage
      context.instances == instances
    }
    0 * _

    where:
    instances = (1..10).collect(this.&mkInstance).collectEntries { [(it.instanceId):it] }
    stageContext = [
      rollingPatch: false
    ]
  }

  def "configures rolling quickpatch"() {
    given:
    def stage = new PipelineStage(new Pipeline(), "quickPatch", stageContext)

    when:
    def steps = quickPatchStage.buildSteps(stage)

    then:
    1 * oortHelper.getInstancesForCluster(_, null, true, false) >> instances
    steps == [step]
    !stage.initializationStage
    stage.status == ExecutionStatus.NOT_STARTED
    stage.afterStages.size() == instances.size()
    def stageInstances = [] as Set
    stage.afterStages.each {
      with(it) {
        name == "bulkQuickPatchStage"
        stageBuilder == bulkQuickPatchStage
        context.instances.size() == 1
        stageInstances.addAll(context.instances.keySet()) == true
      }
    }
    stageInstances.sort() == instances.keySet().sort()
    0 * _

    where:
    instances = (1..10).collect(this.&mkInstance).collectEntries { [(it.instanceId):it] }
    stageContext = [
      rollingPatch: true
    ]
  }

  private Map mkInstance(int id) {
    [
      instanceId: "i-$id".toString(),
      hostName: "h${id}.foo.com",
      healthCheckUrl: "/health"
    ]
  }

  /**
   * This noops out the spring batch aspects of stage building which aren't really a concern for the purposes
   * of this test
   */
  @InheritConstructors
  static class NoBatchQuickPatchStage extends QuickPatchStage {
    Step step

    @Override
    protected Step buildStep(Stage stage, String taskName, Class<? extends Task> taskType, StepExecutionListener... listeners) {
      return step
    }
  }
}
