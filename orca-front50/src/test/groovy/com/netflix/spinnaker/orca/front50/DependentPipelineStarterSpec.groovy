/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.front50

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.ExecutionLauncher
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.ManualTrigger
import com.netflix.spinnaker.orca.pipeline.model.Trigger
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import com.netflix.spinnaker.security.AuthenticatedRequest
import org.slf4j.MDC
import org.springframework.context.support.StaticApplicationContext
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class DependentPipelineStarterSpec extends Specification {

  @Subject
  DependentPipelineStarter dependentPipelineStarter

  ObjectMapper mapper = OrcaObjectMapper.newInstance()

  def "should propagate credentials from explicit pipeline invocation ('run pipeline' stage)"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def gotMDC = [:]
    def executionLauncher = Stub(ExecutionLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[1], Map)
        return pipeline {
          name = p.name
          id = p.name
          trigger = mapper.convertValue(p.trigger, Trigger)
        }
      }
    }
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    def mdc = [
      (AuthenticatedRequest.SPINNAKER_USER)    : "myMDCUser",
      (AuthenticatedRequest.SPINNAKER_ACCOUNTS): "acct3,acct4"
    ]
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor()
    )

    when:
    MDC.setContextMap(mdc)
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline, [:],
      "parent"
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "myMDCUser"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct3,acct4"
  }

  def "should propagate credentials from implicit pipeline invocation (listener for pipeline completion)"() {
    setup:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def gotMDC = [:]
    def executionLauncher = Stub(ExecutionLauncher) {
      start(*_) >> {
        gotMDC.putAll(MDC.copyOfContextMap)
        def p = mapper.readValue(it[1], Map)
        return pipeline {
          name = p.name
          id = p.name
          trigger = mapper.convertValue(p.trigger, Trigger)
        }
      }
    }
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor()
    )

    when:
    MDC.clear()
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      "parent"
    )
    MDC.clear()

    then:
    result?.name == "triggered"
    gotMDC["X-SPINNAKER-USER"] == "parentUser"
    gotMDC["X-SPINNAKER-ACCOUNTS"] == "acct1,acct2"
  }

  def "should not do anything if parent was a dry run execution"() {
    given:
    def triggeredPipelineConfig = [name: "triggered", id: "triggered"]
    def parentPipeline = pipeline {
      name = "parent"
      trigger = new ManualTrigger(null, "fzlem@netflix.com", [:], [], [])
      trigger.otherProperties.dryRun = true
      authentication = new Execution.AuthenticationDetails("parentUser", "acct1", "acct2")
    }
    def executionLauncher = Mock(ExecutionLauncher)
    def applicationContext = new StaticApplicationContext()
    applicationContext.beanFactory.registerSingleton("pipelineLauncher", executionLauncher)
    dependentPipelineStarter = new DependentPipelineStarter(
      objectMapper: mapper,
      applicationContext: applicationContext,
      contextParameterProcessor: new ContextParameterProcessor()
    )

    when:
    def result = dependentPipelineStarter.trigger(
      triggeredPipelineConfig,
      null /*user*/,
      parentPipeline,
      [:],
      "parent"
    )

    then:
    result == null

    and:
    0 * executionLauncher._
  }
}
