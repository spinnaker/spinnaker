/*
 * Copyright 2020 Armory
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

package com.netflix.spinnaker.credentials;

/**
 * After {@link Credentials} have been parsed, they can be activated, refreshed, or retired - e.g.
 * adding agents. This happens before credentials are added or updated in the {@link
 * CredentialsRepository} and after credentials are removed from the {@link CredentialsRepository}.
 *
 * @param <T>
 */
public interface CredentialsLifecycleHandler<T extends Credentials> {

  /**
   * Credentials have been added. This is called before credentials are available in {@link
   * CredentialsRepository}
   *
   * @param credentials
   */
  void credentialsAdded(T credentials);

  /**
   * Credentials have been updated. This is called before credentials are updated in {@link
   * CredentialsRepository}
   *
   * @param credentials
   */
  void credentialsUpdated(T credentials);

  /**
   * Credentials have been deleted. This is called after credentials are removed from {@link
   * CredentialsRepository}
   *
   * @param credentials
   */
  void credentialsDeleted(T credentials);
}
