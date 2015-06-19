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

package com.netflix.spinnaker.orca.pipeline.persistence.memory

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionNotFoundException
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionStore
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.springframework.beans.factory.annotation.Autowired
import rx.Subscriber

/**
 * In-memory implementation of {@link ExecutionStore} intended for use in testing
 */
@CompileStatic
abstract class AbstractInMemoryStore<T extends Execution> implements ExecutionStore<T> {

  @Delegate(includes = "clear", interfaces = false)
  protected final Map<String, String> executions = [:]
  protected final Map<String, List<String>> executionsForApps = [:]
  protected final Map<String, String> stages = [:]

  String prefix
  Class<T> executionClass
  ObjectMapper mapper

  @Autowired
  AbstractInMemoryStore(String prefix, Class<T> executionClass, ObjectMapper mapper) {
    this.prefix = prefix
    this.executionClass = executionClass
    this.mapper = mapper
  }

  @Override
  void store(T execution) {
    def executionJson
    if (!execution.id) {
      execution.id = UUID.randomUUID().toString()
      executionJson = mapper.writeValueAsString(execution)
      if (!executionsForApps.containsKey(execution.application)) {
        executionsForApps[execution.application] = []
      }
      ((List)executionsForApps[execution.application]) << executionJson
    } else {
      executionJson = mapper.writeValueAsString(execution)
    }
    executions[execution.id] = executionJson
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @Override
  T retrieve(String id) {
    if (!executions.containsKey(id)) {
      throw new ExecutionNotFoundException("No ${prefix} execution found for $id")
    }
    T execution = (T)mapper.readValue(executions[id], executionClass)

    def reorderedStages = []
    execution.stages.findAll { it.parentStageId == null }.each { Stage<T> parentStage ->
      reorderedStages << parentStage

      def children = new LinkedList<Stage<T>>(execution.stages.findAll { it.parentStageId == parentStage.id })
      while (!children.isEmpty()) {
        def child = children.remove(0)
        children.addAll(0, execution.stages.findAll { it.parentStageId == child.id})
        reorderedStages << child
      }
    }
    execution.stages = reorderedStages.collect {
      def explicitStage = retrieveStage(it.id) ?: it
      explicitStage.execution = execution
      return explicitStage
    }
    execution
  }

  @Override
  void delete(String id) {
    try {
      T item = retrieve(id)
      executionsForApps[item.application].remove(mapper.writeValueAsString(item))
      executions.remove(id)
      item.stages.each { Stage stage ->
        def stageKey = "${prefix}:stage:${stage.id}"
        stages.remove(stageKey)
      }
    } catch (ExecutionNotFoundException ignored) { }
  }

  @Override
  void storeStage(Stage<T> stage) {
    def key = "${prefix}:stage:${stage.id}" as String
    stages[key] = mapper.writeValueAsString(stage)
  }

  @Override
  Stage<T> retrieveStage(String id) {
    def key = "${prefix}:stage:${id}" as String
    return stages[key] ? mapper.readValue(stages[key], Stage) : null
  }

  @Override
  List<Stage<T>> retrieveStages(List<String> ids) {
    ids.collect { retrieveStage(it) }
  }

  @Override
  @CompileDynamic
  rx.Observable<T> all() {
    return rx.Observable.create(new rx.Observable.OnSubscribe<T>() {
      @Override
      public void call(Subscriber<? super T> observer) {
        try {
          if (!observer.isUnsubscribed()) {
            executions.keySet().collect {
              observer.onNext(retrieve(it))
            }
            observer.onCompleted()
          }
        } catch (Exception e) {
          observer.onError(e)
        }
      }
    })
  }

  @Override
  @CompileDynamic
  rx.Observable<T> allForApplication(String app) {
    return rx.Observable.create(new rx.Observable.OnSubscribe<T>() {
      @Override
      public void call(Subscriber<? super T> observer) {
        try {
          if (!observer.isUnsubscribed()) {
            if (executionsForApps.containsKey(app)) {
              executionsForApps[app].collect {
                def execution = mapper.readValue(it, executionClass) as Execution<T>
                observer.onNext(retrieve(execution.id))
              }
            }
            observer.onCompleted()
          }
        } catch (Exception e) {
          observer.onError(e)
        }
      }
    })
  }
}
