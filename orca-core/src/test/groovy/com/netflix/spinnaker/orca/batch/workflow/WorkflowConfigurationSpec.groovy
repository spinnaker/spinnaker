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

package com.netflix.spinnaker.orca.batch.workflow

import spock.lang.Specification
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.JobStarter
import com.netflix.spinnaker.orca.workflow.WorkflowBuilder
import org.springframework.context.support.StaticApplicationContext

class WorkflowConfigurationSpec extends Specification {

  def jobStarter = new JobStarter()
  def fooWorkflowBuilder = Mock(WorkflowBuilder)

  def setup() {
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("fooWorkflowBuilder", fooWorkflowBuilder)

    jobStarter.applicationContext = applicationContext
  }

  def "a single workflow step is constructed from mayo's json config"() {
    given:
    def mapper = new ObjectMapper()
    def config = mapper.writeValueAsString([
      [type: "foo"]
    ])

    when:
    jobStarter.start(config)

    then:
    1 * fooWorkflowBuilder.build(_)
  }

}
