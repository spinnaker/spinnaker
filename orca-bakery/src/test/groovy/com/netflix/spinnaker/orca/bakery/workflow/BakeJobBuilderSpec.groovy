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

package com.netflix.spinnaker.orca.bakery.workflow

import spock.lang.Specification
import spock.lang.Subject
import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.SimpleJob
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.tasklet.TaskletStep
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.support.StaticApplicationContext
import org.springframework.transaction.PlatformTransactionManager

class BakeJobBuilderSpec extends Specification {

  @Subject builder = new BakeWorkflowBuilder()

  def applicationContext = new StaticApplicationContext()
  def txMan = Stub(PlatformTransactionManager)
  def repository = Stub(JobRepository)
  def jobs = new JobBuilderFactory(repository)
  def steps = new StepBuilderFactory(repository, txMan)

  def bakery = Mock(BakeryService)

  def setup() {
    registerMockBean("bakery", bakery)

    builder.steps = steps
    builder.applicationContext = applicationContext
  }

  def "builds a bake workflow"() {
    when:
    def job = builder.build(jobs.get("BakeJobBuilderSpecJob")).build()

    then:
    job instanceof SimpleJob
    def steps = job.stepNames.collect {
      job.getStep(it)
    }
    steps.size() == 2
    steps.every { it instanceof TaskletStep }
    steps.every { ((TaskletStep) it).tasklet instanceof TaskTaskletAdapter }
    steps.tasklet.taskType == [CreateBakeTask, MonitorBakeTask]
  }

  private void registerMockBean(String name, bean) {
    applicationContext.registerBeanDefinition name, new GenericBeanDefinition(source: bean)
  }
}
