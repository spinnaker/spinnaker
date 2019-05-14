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

import java.util.Set;

/**
 * Represents a repository for CRUD operations pertaining to {@link AccountCredentials}. May be
 * required by the {@link AccountCredentialsProvider} to get a handle on credentials objects.
 * Consumers should use this repository interface for manipulating the backing of the provider.
 */
public interface AccountCredentialsRepository {

  /**
   * Returns a single {@link AccountCredentials} object, referenced by the specified name
   *
   * @param key the key to retrieve from the repository
   * @return account credentials
   */
  AccountCredentials getOne(String key);

  /**
   * Returns all {@link AccountCredentials} objects known to this repository
   *
   * @return a set of account credentials
   */
  Set<? extends AccountCredentials> getAll();

  /**
   * Stores an {@link AccountCredentials} object at this repository. This is an identify function.
   *
   * @param key the key to associate with this account credentials object
   * @param credentials account credentials object to save
   * @return input
   */
  AccountCredentials save(String key, AccountCredentials credentials);

  /**
   * Indicates that the keyed reference should be updated with the provided {@link
   * AccountCredentials} object. This is an identify function.
   *
   * @param key the key to associate with this account credentials object
   * @param credentials account credentials object to associate with the provided key
   * @return input
   */
  AccountCredentials update(String key, AccountCredentials credentials);

  /**
   * Should remove the keyed reference from the repository
   *
   * @param key ref to be removed
   */
  void delete(String key);
}
