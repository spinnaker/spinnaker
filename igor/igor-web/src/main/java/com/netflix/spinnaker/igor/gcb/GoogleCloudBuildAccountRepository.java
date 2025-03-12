/*
 * Copyright 2019 Google, Inc.
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

package com.netflix.spinnaker.igor.gcb;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedMap;
import com.netflix.spinnaker.kork.web.exceptions.NotFoundException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * Keeps track of all registered instances of GoogleCloudBuildAccount and returns the appropriate
 * account when it is requested by name.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class GoogleCloudBuildAccountRepository {
  private final ImmutableSortedMap<String, GoogleCloudBuildAccount> accounts;

  ImmutableList<String> getAccounts() {
    return accounts.keySet().asList();
  }

  GoogleCloudBuildAccount getGoogleCloudBuild(String name) {
    GoogleCloudBuildAccount account = accounts.get(name);
    if (account == null) {
      throw new NotFoundException(
          String.format("No Google Cloud Build account with name %s is configured", name));
    }
    return account;
  }

  static Builder builder() {
    return new Builder();
  }

  @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
  static final class Builder {
    private final ImmutableSortedMap.Builder<String, GoogleCloudBuildAccount> accounts =
        ImmutableSortedMap.naturalOrder();

    Builder registerAccount(String name, GoogleCloudBuildAccount account) {
      accounts.put(name, account);
      return this;
    }

    GoogleCloudBuildAccountRepository build() {
      return new GoogleCloudBuildAccountRepository(accounts.build());
    }
  }
}
