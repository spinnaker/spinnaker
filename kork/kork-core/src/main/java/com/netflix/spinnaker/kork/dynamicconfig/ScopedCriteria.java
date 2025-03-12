/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.dynamicconfig;

public class ScopedCriteria {
  public final String region;
  public final String account;
  public final String cloudProvider;
  public final String application;

  ScopedCriteria(String region, String account, String cloudProvider, String application) {
    this.region = region;
    this.account = account;
    this.cloudProvider = cloudProvider;
    this.application = application;
  }

  public static class Builder {
    String region;
    String account;
    String cloudProvider;
    String application;

    public Builder withRegion(String region) {
      this.region = region;
      return this;
    }

    public Builder withAccount(String account) {
      this.account = account;
      return this;
    }

    public Builder withCloudProvider(String cloudProvider) {
      this.cloudProvider = cloudProvider;
      return this;
    }

    public Builder withApplication(String application) {
      this.application = application;
      return this;
    }

    public ScopedCriteria build() {
      return new ScopedCriteria(region, account, cloudProvider, application);
    }
  }

  @Override
  public String toString() {
    return "ScopedCriteria{"
        + "region='"
        + region
        + '\''
        + ", account='"
        + account
        + '\''
        + ", cloudProvider='"
        + cloudProvider
        + '\''
        + ", application='"
        + application
        + '\''
        + '}';
  }
}
