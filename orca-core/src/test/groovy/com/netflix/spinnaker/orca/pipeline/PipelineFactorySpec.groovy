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

package com.netflix.spinnaker.orca.pipeline

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.apache.commons.lang.math.RandomUtils
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.explore.JobExplorer
import spock.lang.Specification
import spock.lang.Subject

class PipelineFactorySpec extends Specification {

  def jobExplorer = Mock(JobExplorer)
  @Subject pipelineFactory = new PipelineFactory(jobExplorer)

  def "can retrieve a pipeline representation"() {
    given: "a pipeline was started"
    def jobExecution = new JobExecution(id)
    jobExecution.executionContext.put("pipeline", Pipeline.builder().withStages(*stageTypes).build())
    jobExplorer.getJobExecution(id) >> jobExecution

    when: "we retrieve a pipeline by id"
    def pipeline = pipelineFactory.retrieve(id.toString())

    then: "we get something back"
    pipeline != null

    and: "it has details of all the stages"
    pipeline.stages.type == stageTypes

    where:
    id = RandomUtils.nextLong()
    stageTypes = ["first", "second"]
  }

}
