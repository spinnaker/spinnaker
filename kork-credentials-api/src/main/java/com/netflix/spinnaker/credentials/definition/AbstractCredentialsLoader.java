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

package com.netflix.spinnaker.credentials.definition;

import com.netflix.spinnaker.credentials.Credentials;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import javax.annotation.PostConstruct;
import lombok.Getter;

public abstract class AbstractCredentialsLoader<T extends Credentials> {
  @Getter protected final CredentialsRepository<T> credentialsRepository;

  public AbstractCredentialsLoader(CredentialsRepository<T> credentialsRepository) {
    this.credentialsRepository = credentialsRepository;
  }

  /**
   * Loads credentials into the given repository. Each implementation should be idempotent. It will
   * typically be called once before the application is ready and might be called on a regular basis
   * thereafter.
   */
  @PostConstruct
  public abstract void load();
}
