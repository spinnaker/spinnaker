/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.kork.web.selector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ByExecutionTypeServiceSelector implements ServiceSelector {
  private final Object service;
  private final int priority;
  private final Set<String> executionTypes;

  @SuppressWarnings("unchecked")
  public ByExecutionTypeServiceSelector(
      Object service, Integer priority, Map<String, Object> config) {
    this.service = service;
    this.priority = priority;
    this.executionTypes =
        new HashSet<>(((Map<String, String>) config.get("executionTypes")).values());
  }

  @Override
  public Object getService() {
    return service;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  @Override
  public boolean supports(SelectableService.Criteria criteria) {
    return executionTypes.contains(criteria.getExecutionType());
  }
}
