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
package com.netflix.spinnaker.orca.pipeline.persistence.memory;

import java.util.*;
import com.netflix.spinnaker.orca.pipeline.persistence.PipelineStack;
import static java.util.Collections.emptyList;
import static java.util.Collections.reverse;

public class InMemoryPipelineStack implements PipelineStack {

  private final Map<String, List<String>> keys = new HashMap<>();

  @Override
  public void add(String id, String content) {
    keys.putIfAbsent(id, new ArrayList<>());
    keys.get(id).add(content);
  }

  @Override
  public boolean addToListIfKeyExists(String id1, String id2, String content) {
    if (keys.keySet().contains(id1)) {
      add(id2, content);
      return true;
    }
    return false;
  }

  @Override public void remove(String id, String content) {
    (keys.get(id)).remove(content);
    if (keys.get(id).isEmpty()) {
      keys.remove(id);
    }
  }

  @Override public boolean contains(String id) {
    return keys.keySet().contains(id);
  }

  @Override public List<String> elements(String id) {
    return Optional
      .ofNullable(keys.get(id))
      .map(it -> {
        reverse(it);
        return it;
      })
      .orElse(emptyList());
  }

}
