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

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.kork.annotations.Beta;
import java.util.Collections;
import java.util.List;

/**
 * Implementations of this interface will provide properties specific to a named account object,
 * with capability to retrieve a type of credential object (such as AWSCredentials or
 * GoogleCredentials).
 *
 * @param <T> - type of credential object to be returned
 */
@Beta
public interface AccountCredentials<T> extends Credentials {
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
   * @return the id for the account (may be null if not supported by underlying cloud provider)
   */
  default String getAccountId() {
    return null;
  }

  /**
   * Returns an associated credentials object, which may be lazily initialized based off of some
   * detail encapsulated within the implementation (like environment or keys, etc)
   *
   * @return typed credentials object
   */
  T getCredentials();

  /**
   * Provides the name of the cloud provider. Typically something like 'aws', 'gce' or 'docker'.
   *
   * @return the name of the cloud provider
   */
  String getCloudProvider();

  @Override
  default String getType() {
    return getCloudProvider();
  }

  default boolean isEnabled() {
    return true;
  }

  /**
   * A user in ANY required group should be allowed access to this account.
   *
   * @return the group names that govern access to this account, empty indicates a public account
   *     accessible by all.
   */
  @Deprecated
  default List<String> getRequiredGroupMembership() {
    return Collections.emptyList();
  }
}
