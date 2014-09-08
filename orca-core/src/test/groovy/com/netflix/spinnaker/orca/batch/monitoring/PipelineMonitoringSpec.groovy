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

  def fooTasklet = Stub(Tasklet)

  def setup() {
    applicationContext.beanFactory.with {
      registerSingleton "mapper", mapper
      registerSingleton "fooStage", new TestStage("foo", fooTasklet, steps)

      autowireBean pipelineStarter
    }
    pipelineStarter.initialize()
  }

  def "the pipeline monitor is notified as stages start and complete"() {
    given:
    fooTasklet.execute(*_) >> RepeatStatus.FINISHED

    when:
    pipelineStarter.start("""[{"type": "$stageName"}]""")

    then:
    1 * pipelineMonitor.beginStage(stageName)

    then:
    1 * pipelineMonitor.endStage(stageName)

    where:
    stageName = "foo"
  }

}
