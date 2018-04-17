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

import org.springframework.util.Assert;

import java.util.List;

public class SelectableService {
  private final List<ServiceSelector> serviceSelectors;

  public SelectableService(List<ServiceSelector> serviceSelectors) {
    this.serviceSelectors = serviceSelectors;
  }

  public Object getService(Criteria criteria) {
    Assert.notNull(criteria);

    return serviceSelectors
      .stream()
      .filter(it -> it.supports(criteria))
      .sorted((a,b) -> b.getPriority() - a.getPriority())
      .findFirst()
      .map(ServiceSelector::getService)
      .orElse(serviceSelectors.get(0).getService());
  }

  public static class Criteria {
    private final String application;
    private final String authenticatedUser;
    private final String executionType;
    private final String executionId;
    private final String origin;

    public Criteria(String application, String authenticatedUser, String executionType, String origin) {
      this(application, authenticatedUser, executionType, null, origin);
    }

    public Criteria(String application,
                    String authenticatedUser,
                    String executionType,
                    String executionId,
                    String origin) {
      this.application = application;
      this.authenticatedUser = authenticatedUser;
      this.executionType = executionType;
      this.executionId = executionId;
      this.origin = origin;
    }

    public String getApplication() {
      return application;
    }

    public String getAuthenticatedUser() {
      return authenticatedUser;
    }

    public String getExecutionType() {
      return executionType;
    }

    public String getExecutionId() {
      return executionId;
    }

    public String getOrigin() {
      return origin;
    }
  }
}
