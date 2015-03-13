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

package com.netflix.spinnaker.orca.pipeline.model

import groovy.transform.CompileStatic

@CompileStatic
class Pipeline extends Execution<Pipeline> {

  String application
  String name
  final Map<String, Object> trigger = [:]
  final Map<String, Serializable> initialConfig = [:]

  static Builder builder() {
    new Builder()
  }

  static class Builder {

    private final Pipeline pipeline = new Pipeline()

    Builder withTrigger(Map<String, Object> trigger = [:]) {
      pipeline.@trigger.clear()
      if (trigger) {
        pipeline.@trigger.putAll(trigger)
      }
      return this
    }

    Builder withAppConfig(Map<String, Serializable> appConfig = [:]) {
      pipeline.@appConfig.clear()
      if (appConfig) {
        pipeline.@appConfig.putAll(appConfig)
      }
      return this
    }

    Builder withStage(String type, String name = type, Map<String, Object> context = [:]) {
      if (context.providerType) {
        type += "_$context.providerType"
      }

      pipeline.stages << new PipelineStage(pipeline, type, name, context)
      return this
    }

    Builder withStages(List<Map<String, Object>> stages) {
      stages.each {
        def type = it.remove("type").toString()
        def name = it.remove("name").toString()
        withStage(type, name ?: type, it)
      }
      return this
    }

    Builder withStages(String... stageTypes) {
      stageTypes.each { String it ->
        withStage(it)
      }
      return this
    }

    Pipeline build() {
      pipeline
    }

    Builder withApplication(String application) {
      pipeline.@application = application
      return this
    }

    Builder withName(String name) {
      pipeline.@name = name
      return this
    }
  }
}
