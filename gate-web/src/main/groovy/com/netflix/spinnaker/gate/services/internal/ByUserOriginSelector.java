/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services.internal;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import com.netflix.spinnaker.kork.web.selector.ServiceSelector;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ByUserOriginSelector implements ServiceSelector {
  private final Object service;
  private final int priority;

  @Nullable private final String origin;

  public ByUserOriginSelector(Object service, Integer priority, Map<String, Object> config) {
    this.service = service;
    this.priority = priority;
    this.origin = (String) config.get("origin");

    if (origin == null) {
      log.warn("ByUserOriginSelector created for service {} has null origin", service);
    }
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
    return origin != null && origin.equals(criteria.getOrigin());
  }
}
