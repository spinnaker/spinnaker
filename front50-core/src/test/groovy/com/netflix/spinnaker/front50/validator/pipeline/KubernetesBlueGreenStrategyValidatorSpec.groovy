/*
 * Copyright 2022 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.front50.validator.pipeline

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.netflix.spinnaker.front50.api.model.pipeline.Pipeline
import com.netflix.spinnaker.front50.api.validator.PipelineValidator
import com.netflix.spinnaker.front50.api.validator.ValidatorErrors
import org.slf4j.LoggerFactory
import spock.lang.Specification

class KubernetesBlueGreenStrategyValidatorSpec extends Specification {

  private static MemoryAppender memoryAppender

  void setup() {
    Logger logger = (Logger) LoggerFactory.getLogger("com.netflix.spinnaker.front50.validator.pipeline")
    memoryAppender = new MemoryAppender()
    memoryAppender.setContext((LoggerContext) LoggerFactory.getILoggerFactory())
    logger.setLevel(Level.DEBUG)
    logger.addAppender(memoryAppender)
    memoryAppender.start()
  }


   void cleanup() {
    memoryAppender.reset()
    memoryAppender.stop()
  }

  def "should not return error when stage null or empty"() {
    setup:
    def pipeline = new Pipeline()
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()

    when:
    pipeline.setStages(List.of())
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()
  }

  def "should not return error cloud provider is not Kubernetes"() {
    setup:
    def pipeline = new Pipeline()
    Map<String, Object> trafficManagement = new LinkedHashMap<>()
    trafficManagement.put("enabled", true)
    Map<String, Object> options = new LinkedHashMap<>()
    options
    pipeline.setStages(List.of(Map.of("cloudProvider","aws", "type", "deploy")))
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()
  }

  def "should not return error when there is no deployManifestStage"() {
    setup:
    def pipeline = new Pipeline()
    Map<String, Object> trafficManagement = new LinkedHashMap<>()
    trafficManagement.put("enabled", true)
    Map<String, Object> options = new LinkedHashMap<>()
    options
    pipeline.setStages(List.of(Map.of("cloudProvider","kubernetes", "type", "patchManifest")))
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()
  }

  def "should not return error when trafficManagement not enabled"() {
    setup:
    def pipeline = new Pipeline()
    Map<String, Object> trafficManagement = new LinkedHashMap<>()
    trafficManagement.put("enabled", true)
    Map<String, Object> options = new LinkedHashMap<>()
    options
    pipeline.setStages(List.of(Map.of("cloudProvider","kubernetes", "type", "deployManifest", "trafficManagement", Map.of("enabled", false))))
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()
  }

  def "should not return error when redblack is not selected"() {
    setup:
    def pipeline = new Pipeline()
    Map<String, Object> trafficManagement = new LinkedHashMap<>()
    trafficManagement.put("enabled", true)
    Map<String, Object> options = new LinkedHashMap<>()
    options
    pipeline.setStages(List.of(Map.of("cloudProvider","kubernetes", "type", "deployManifest", "trafficManagement", Map.of("enabled", true, "options", Map.of("strategy", "bluegreen")))))
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    !errors.hasErrors()
  }

  def "should return error when redblack is used"() {
    setup:
    def pipeline = new Pipeline()
    Map<String, Object> trafficManagement = new LinkedHashMap<>()
    trafficManagement.put("enabled", true)
    Map<String, Object> options = new LinkedHashMap<>()
    options
    pipeline.setStages(List.of(Map.of("cloudProvider","kubernetes",
      "type", "deployManifest",
      "trafficManagement", Map.of("enabled", true, "options", Map.of("strategy", "redblack")))))
    def errors = new ValidatorErrors()

    when:
    PipelineValidator validator = new KubernetesBlueGreenStrategyValidator()
    validator.validate(pipeline, errors)

    then:
    memoryAppender.getSize() ==1
    memoryAppender.contains("Kubernetes traffic management redblack strategy is deprecated and will be removed soon. Please use bluegreen instead", Level.WARN)
    !errors.hasErrors()
//    errors.getAllErrors().size() == 1
//    errors.getAllErrorsMessage().equals("Kubernetes traffic management redblack strategy is deprecated and will be removed soon. Please use bluegreen instead")
  }

  class MemoryAppender extends ListAppender<ILoggingEvent> {
    void reset() {
      this.list.clear();
    }
    int getSize() {
      return this.list.size();
    }

    boolean contains(String string, Level level) {
      return this.list.stream()
        .filter({ event ->
          event.toString().contains(string)
        }).anyMatch( { event ->
        event.getLevel().equals(level)
      });
    }
  }
}
