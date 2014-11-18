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

package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import org.springframework.batch.core.JobExecution
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.core.scope.context.StepContext
import spock.lang.Specification
import spock.lang.Subject
import static org.springframework.batch.repeat.RepeatStatus.FINISHED

class PipelineInitializerTaskletSpec extends Specification {

  def jobExecution = new JobExecution(0L)
  def stepExecution = new StepExecution("whatever", jobExecution)
  def stepContext = new StepContext(stepExecution)
  def stepContribution = new StepContribution(stepExecution)
  def chunkContext = new ChunkContext(stepContext)

  def pipeline = Pipeline.builder().withApplication("app").withStage("stage1").build()
  @Subject tasklet = new PipelineInitializerTasklet()

  def "always returns finished status"() {
    expect:
    tasklet.execute(stepContribution, chunkContext) == FINISHED
  }

}
