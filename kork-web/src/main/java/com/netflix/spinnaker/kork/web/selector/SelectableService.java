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

import java.util.List;
import org.springframework.util.Assert;

public class SelectableService<T> {
  private final List<ServiceSelector<T>> serviceSelectors;

  public SelectableService(List<ServiceSelector<T>> serviceSelectors) {
    this.serviceSelectors = serviceSelectors;
  }

  public T getService(Criteria criteria) {
    Assert.notNull(criteria, "Criteria is required to select a service");

    return serviceSelectors.stream()
        .filter(it -> it.supports(criteria))
        .min((a, b) -> b.getPriority() - a.getPriority())
        .map(ServiceSelector::getService)
        .orElse(serviceSelectors.get(0).getService());
  }

  public static class Criteria {
    private String account;
    private String application;
    private String authenticatedUser;
    private String cloudProvider;
    private String executionType;
    private String executionId;
    private String origin;
    private String location;

    public String getAccount() {
      return account;
    }

    public String getApplication() {
      return application;
    }

    public String getAuthenticatedUser() {
      return authenticatedUser;
    }

    public String getCloudProvider() {
      return cloudProvider;
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

    public String getLocation() {
      return location;
    }

    public Criteria withAccount(String account) {
      this.account = account;
      return this;
    }

    public Criteria withApplication(String application) {
      this.application = application;
      return this;
    }

    public Criteria withAuthenticatedUser(String user) {
      this.authenticatedUser = user;
      return this;
    }

    public Criteria withCloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
      return this;
    }

    public Criteria withOrigin(String origin) {
      this.origin = origin;
      return this;
    }

    public Criteria withExecutionType(String executionType) {
      this.executionType = executionType;
      return this;
    }

    public Criteria withExecutionId(String executionId) {
      this.executionId = executionId;
      return this;
    }

    public Criteria withLocation(String location) {
      this.location = location;
      return this;
    }
  }
}
