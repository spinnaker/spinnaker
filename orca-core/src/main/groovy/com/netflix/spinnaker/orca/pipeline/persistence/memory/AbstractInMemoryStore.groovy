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
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.springframework.beans.factory.annotation.Autowired

/**
 * In-memory implementation of {@link ExecutionStore} intended for use in testing
 */
@CompileStatic
abstract class AbstractInMemoryStore<T extends Execution> implements ExecutionStore<T> {

  @Delegate(includes = "clear", interfaces = false)
  protected final Map<String, String> executions = [:]

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
    if (!execution.id) {
      execution.id = UUID.randomUUID().toString()
    }
    executions[execution.id] = mapper.writeValueAsString(execution)
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  @Override
  T retrieve(String id) {
    if (!executions.containsKey(id)) {
      throw new ExecutionNotFoundException("No ${prefix} execution found for $id")
    }
    T execution = (T)mapper.readValue(executions[id], executionClass)
    for (stage in execution.stages) {
      ((Stage<T>)stage).execution = execution
    }
    execution
  }

  @Override
  List<T> all() {
    executions.values().collect {
      mapper.readValue(it, executionClass)
    }
  }
}
