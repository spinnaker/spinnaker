/*
 * Copyright 2022 JPMorgan Chase & Co
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

/**
 * Provides logic to select a service from a configured list of options (e.g. a Clouddriver service
 * in Orca) using the cloudProvider as a criteria. This can be used to send traffic to a specific
 * service endpoint based on the cloud provider the traffic is touching.
 *
 * <p>Example usage in Orca
 *
 * <pre>
 * clouddriver:
 *   readonly:
 *     baseUrls:
 *     - baseUrl: https://clouddriver-readonly-orca-1.example.com
 *       priority: 10
 *       config:
 *         selectorClass: com.netflix.spinnaker.kork.web.selector.ByCloudProviderServiceSelector
 *         cloudProviders:
 *           - kubernetes
 *           - titus
 * </pre>
 */
public class ByCloudProviderServiceSelector implements ServiceSelector {
  private final Object service;
  private final int priority;
  private final Set<String> cloudProviders;

  @SuppressWarnings("unchecked")
  public ByCloudProviderServiceSelector(
      Object service, Integer priority, Map<String, Object> config) {
    this.service = service;
    this.priority = priority;
    this.cloudProviders =
        new HashSet<>(((Map<String, String>) config.get("cloudProviders")).values());
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
    return cloudProviders.contains(criteria.getCloudProvider());
  }
}
