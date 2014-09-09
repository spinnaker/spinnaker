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

package com.netflix.spinnaker.orca.batch.monitoring

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.batch.pipeline.TestStage
import com.netflix.spinnaker.orca.monitoring.PipelineMonitor
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.support.AbstractApplicationContext
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD

@ContextConfiguration(classes = [BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_EACH_TEST_METHOD)
class PipelineMonitoringSpec extends Specification {

  def pipelineMonitor = Mock(PipelineMonitor)

  @Subject pipelineStarter = new PipelineStarter()

  @Autowired AbstractApplicationContext applicationContext
  @Autowired StepBuilderFactory steps

  @Shared mapper = new ObjectMapper()

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
    }
  }

  private void setupStages(String name, Tasklet... tasklets) {
    def stage = new TestStage(name, steps, pipelineMonitor)
    tasklets.each { stage << it }
    applicationContext.beanFactory.with {
      registerSingleton "${name}Stage", stage
      autowireBean pipelineStarter
    }
    pipelineStarter.initialize()
  }

  def "a stage with a single task raises begin and end stage events"() {
    given: "a stage with a single task"
    def tasklet1 = Stub(Tasklet) {
      execute(*_) >> RepeatStatus.FINISHED
    }
    setupStages stageName, tasklet1

    when: "the pipeline runs"
    pipelineStarter.start("""[{"type": "$stageName"}]""")

    then: "we get an event at the start of the stage"
    1 * pipelineMonitor.beginStage(stageName)

    then: "we get an event at the start of the task"
    1 * pipelineMonitor.beginTask()

    then: "we get an event at the end of the task"
    1 * pipelineMonitor.endTask()

    then: "we get an event at the end of the stage"
    1 * pipelineMonitor.endStage(stageName)

    where:
    stageName = "foo"
  }

  def "a stage with multiple tasks raises a single begin and end stage event"() {
    given: "a stage with a single task"
    def tasklet1 = Stub(Tasklet) {
      execute(*_) >> RepeatStatus.FINISHED
    }
    def tasklet2 = Stub(Tasklet) {
      execute(*_) >> RepeatStatus.FINISHED
    }
    setupStages stageName, tasklet1, tasklet2

    when: "the pipeline runs"
    pipelineStarter.start("""[{"type": "$stageName"}]""")

    then: "we get an event at the start of the stage"
    1 * pipelineMonitor.beginStage(stageName)

    then: "we get an event at the start and end of each task"
    2 * pipelineMonitor.beginTask()
    2 * pipelineMonitor.endTask()

    then: "we get an event at the end of the stage"
    1 * pipelineMonitor.endStage(stageName)

    where:
    stageName = "foo"
  }

}
