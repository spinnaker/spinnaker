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

import com.netflix.spinnaker.kork.exceptions.MissingCredentialsException;
import com.netflix.spinnaker.kork.exceptions.UnknownCredentialsTypeException;
import java.util.*;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

/**
 * Provides access to credentials (or extension of Credentials) across credentials types.
 *
 * @param <T>
 */
public class CompositeCredentialsRepository<T extends Credentials> {
  private Map<String, CredentialsRepository<? extends T>> allRepositories;

  public CompositeCredentialsRepository(List<CredentialsRepository<? extends T>> repositories) {
    allRepositories = new HashMap<>();
    repositories.forEach(this::registerRepository);
  }

  public void registerRepository(CredentialsRepository<? extends T> repository) {
    allRepositories.put(repository.getType(), repository);
  }

  public T getCredentials(String credentialsName, String type) {
    CredentialsRepository<? extends T> repository = allRepositories.get(type);
    if (repository == null) {
      throw new UnknownCredentialsTypeException("No credentials of type '" + type + "' found");
    }

    T creds = repository.getOne(credentialsName);
    if (creds == null) {
      throw new MissingCredentialsException(
          "Credentials '" + credentialsName + "' of type '" + type + "' cannot be found");
    }
    return creds;
  }

  /**
   * Helper method during migration from single to multiple credential repositories
   *
   * @param name
   * @return Account with the given name across all repositories
   */
  @Nullable
  public T getFirstCredentialsWithName(String name) {
    return allRepositories.values().stream()
        .map(r -> r.getOne(name))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(null);
  }

  /**
   * @return All credentials across all repositories
   */
  public List<T> getAllCredentials() {
    return Collections.unmodifiableList(
        allRepositories.values().stream()
            .map(CredentialsRepository::getAll)
            .flatMap(Collection::stream)
            .collect(Collectors.toList()));
  }
}
