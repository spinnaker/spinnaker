/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementations of this interface will provide properties specific to a named account object,
 * with capability to retrieve a type of credential object (such as AWSCredentials or
 * GoogleCredentials).
 *
 * @param <T> - type of credential object to be returned
 */
public interface AccountCredentials<T> {
  /**
   * Provides the name of the account to be returned.
   *
   * <p>Uniquely identifies the account.
   *
   * @return the name of the account
   */
  String getName();

  /**
   * Provides the environment name for the account.
   *
   * <p>Many accounts can share the same environment (e.g. dev, test, prod)
   *
   * @return the Environment name
   */
  String getEnvironment();

  /**
   * Provides the type for the account.
   *
   * <p>Account type is typically consistent among the set of credentials that represent a related
   * set of environments.
   *
   * <p>e.g.:
   *
   * <ul>
   *   <li>account name: maindev, environment: dev, accountType: main
   *   <li>account name: maintest, environment: test, accountType: main
   *   <li>account name: mainprod, environment: prod, accountType: main
   * </ul>
   *
   * @return the type for the account.
   */
  String getAccountType();

  /**
   * Provides the "version" of the account's provider. If an account has been configured at a
   * particular version, it can be supported by different caching agents and operation converters.
   * By default every account is at version v1.
   *
   * @return the account's version.
   */
  default ProviderVersion getProviderVersion() {
    return ProviderVersion.v1;
  }

  /**
   * Provides a named "skin" as a signal for Spinnaker API clients, e.g. Deck, to alter their
   * behavior. By default, returns an account's provider version, but does not need to be coupled to
   * a provider version.
   *
   * @return the account's skin.
   */
  default String getSkin() {
    return getProviderVersion().toString();
  }

  /** @return the id for the account (may be null if not supported by underlying cloud provider) */
  default String getAccountId() {
    return null;
  }

  /**
   * Returns an associated credentials object, which may be lazily initialized based off of some
   * detail encapsulated within the implementation (like environment or keys, etc)
   *
   * @return typed credentials object
   */
  @JsonIgnore
  T getCredentials();

  /**
   * Provides the name of the cloud provider. Typically something like 'aws', 'gce' or 'docker'.
   *
   * @return the name of the cloud provider
   */
  String getCloudProvider();

  /**
   * A user in ANY required group should be allowed access to this account.
   *
   * @return the group names that govern access to this account, empty indicates a public account
   *     accessible by all.
   */
  @Deprecated
  List<String> getRequiredGroupMembership();

  default Permissions getPermissions() {
    Set<String> rgm =
        Optional.ofNullable(getRequiredGroupMembership())
            .map(
                l ->
                    l.stream()
                        .map(
                            s ->
                                Optional.ofNullable(s)
                                    .map(String::trim)
                                    .map(String::toLowerCase)
                                    .orElse(""))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet()))
            .orElse(Collections.EMPTY_SET);
    if (rgm.isEmpty()) {
      return Permissions.EMPTY;
    }

    Permissions.Builder perms = new Permissions.Builder();
    for (String role : rgm) {
      perms.add(Authorization.READ, role);
      perms.add(Authorization.WRITE, role);
    }
    return perms.build();
  }

  default boolean isEnabled() {
    return true;
  }
}
