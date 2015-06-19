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

import com.netflix.spinnaker.orca.pipeline.persistence.PipelineQueue

class InMemoryPipelineQueue implements PipelineQueue {

  Map<String, List<String>> keys = [:]

  void add(String id, String content) {
    if (!keys[id]) {
      keys[id] = []
    }
    (keys[id]).add(content)
  }

  void remove(String id, String content) {
    (keys[id]).remove(content)
    if (keys[id].empty) {
      keys.remove(id)
    }
  }

  boolean contains(String id) {
    keys.keySet().contains(id)
  }

  List<String> elements(String id) {
    keys[id] ? keys[id].reverse() : []
  }

}
