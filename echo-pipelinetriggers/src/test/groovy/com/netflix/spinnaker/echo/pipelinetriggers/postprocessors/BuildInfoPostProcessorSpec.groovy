/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.postprocessors

import com.netflix.spinnaker.echo.services.IgorService
import com.netflix.spinnaker.echo.test.RetrofitStubs
import com.netflix.spinnaker.kork.core.RetrySupport
import spock.lang.Specification
import spock.lang.Subject

class BuildInfoPostProcessorSpec extends Specification implements RetrofitStubs {
  static String JOB_NAME = "test-job"
  static String MASTER_NAME = "test-master"
  static int BUILD_NUMBER = 123
  static String PROPERTY_FILE = "test-property-file"
  static Map<String, Object> BUILD_INFO = [
    abc: 123
  ]
  static Map<String, Object> PROPERTIES = [
    def: 456
  ]

  def igorService = Mock(IgorService)
  def retrySupport = new RetrySupport()

  @Subject
  def buildInfoPostProcessor = new BuildInfoPostProcessor(igorService, retrySupport)

  def "does not error if the input pipeline trigger is null"() {
    given:
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(null)

    when:
    def outputPipeline = buildInfoPostProcessor.processPipeline(inputPipeline)

    then:
    0 * igorService.getBuild(*_)
    outputPipeline.trigger == null
  }

  def "does not error if the trigger has no build info"() {
    given:
    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(enabledJenkinsTrigger)

    when:
    def outputPipeline = buildInfoPostProcessor.processPipeline(inputPipeline)

    then:
    0 * igorService.getBuild(*_)
    outputPipeline.trigger.id == enabledJenkinsTrigger.id
  }

  def "fetches build info if defined"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)

    when:
    def outputPipeline = buildInfoPostProcessor.processPipeline(inputPipeline)

    then:
    1 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> BUILD_INFO
    outputPipeline.trigger.buildInfo.equals(BUILD_INFO)
  }

  def "fetches property file if defined"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)
      .withPropertyFile(PROPERTY_FILE)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)

    when:
    def outputPipeline = buildInfoPostProcessor.processPipeline(inputPipeline)

    then:
    1 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> BUILD_INFO
    1 * igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME) >> PROPERTIES
    outputPipeline.trigger.buildInfo.equals(BUILD_INFO)
    outputPipeline.trigger.properties.equals(PROPERTIES)
  }

  def "retries on failure to communicate with igor"() {
    given:
    def trigger = enabledJenkinsTrigger
      .withMaster(MASTER_NAME)
      .withJob(JOB_NAME)
      .withBuildNumber(BUILD_NUMBER)
      .withPropertyFile(PROPERTY_FILE)

    def inputPipeline = createPipelineWith(enabledJenkinsTrigger).withTrigger(trigger)

    when:
    def outputPipeline = buildInfoPostProcessor.processPipeline(inputPipeline)

    then:
    2 * igorService.getBuild(BUILD_NUMBER, MASTER_NAME, JOB_NAME) >> { throw new RuntimeException() } >> BUILD_INFO
    1 * igorService.getPropertyFile(BUILD_NUMBER, PROPERTY_FILE, MASTER_NAME, JOB_NAME) >> PROPERTIES
    outputPipeline.trigger.buildInfo.equals(BUILD_INFO)
    outputPipeline.trigger.properties.equals(PROPERTIES)
  }
}
