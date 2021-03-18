/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.limits;

import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

public class ImplementationLimits {
  private final ServiceLimits defaults;
  private final Map<String, ServiceLimits> accountOverrides;

  public ImplementationLimits(ServiceLimits defaults, Map<String, ServiceLimits> accountOverrides) {
    this.defaults = defaults == null ? new ServiceLimits(null) : defaults;
    this.accountOverrides =
        accountOverrides == null ? Collections.emptyMap() : ImmutableMap.copyOf(accountOverrides);
  }

  public Double getLimit(String limit, String account) {
    return Optional.ofNullable(account)
        .map(accountOverrides::get)
        .map(sl -> sl.getLimit(limit))
        .orElse(defaults.getLimit(limit));
  }
}
