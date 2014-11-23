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

package com.netflix.spinnaker.orca.pipeline.model

import com.google.common.collect.ImmutableList
import groovy.transform.CompileStatic

@CompileStatic
abstract class Execution<T> implements Serializable {
  String id
  List<Stage<T>> stages = []

  Stage namedStage(String type) {
    stages.find {
      it.type == type
    }
  }

  Execution<T> asImmutable() {
    def self = this

    new Execution<T>() {
      @Override
      String getId() {
        self.id
      }

      @Override
      void setId(String id) {

      }

      @Override
      List<Stage> getStages() {
        ImmutableList.copyOf(self.stages)
      }

      @Override
      void setStages(List<Stage<T>> stages) {

      }

      @Override
      Stage namedStage(String type) {
        self.namedStage(type).asImmutable()
      }

      @Override
      Execution asImmutable() {
        this
      }
    }
  }
}
