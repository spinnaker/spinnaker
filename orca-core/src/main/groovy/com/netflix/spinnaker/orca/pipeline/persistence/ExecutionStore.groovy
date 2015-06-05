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

package com.netflix.spinnaker.orca.pipeline.persistence

import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage

interface ExecutionStore<T extends Execution> {
  public static final String ORCHESTRATION = "orchestration"
  public static final String PIPELINE = "pipeline"

  /**
   * @param execution - {@link com.netflix.spinnaker.orca.pipeline.model.Orchestration}, {@link Pipeline}
   */
  void store(T execution)

  void storeStage(Stage<T> stage)

  /**
   * @param id The id of the execution to retrievePipeline.
   * @return The execution implementation's instance
   * @throws ExecutionNotFoundException if <code>id</code> does not exist in the store.
   */
  T retrieve(String id) throws ExecutionNotFoundException

  void delete(String id)

  Stage<T> retrieveStage(String id)

  rx.Observable<T> all()

  rx.Observable<T> allForApplication(String application)
}
