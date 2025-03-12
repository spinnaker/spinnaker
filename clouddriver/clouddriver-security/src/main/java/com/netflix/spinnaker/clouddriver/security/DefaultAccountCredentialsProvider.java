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

import com.netflix.spinnaker.credentials.CompositeCredentialsRepository;
import java.util.Collections;
import java.util.Set;

public class DefaultAccountCredentialsProvider implements AccountCredentialsProvider {
  private final AccountCredentialsRepository repository;
  private final CompositeCredentialsRepository<AccountCredentials<?>> compositeRepository;

  public DefaultAccountCredentialsProvider() {
    this(new MapBackedAccountCredentialsRepository());
  }

  public DefaultAccountCredentialsProvider(AccountCredentialsRepository repository) {
    this(repository, new CompositeCredentialsRepository<>(Collections.emptyList()));
  }

  public DefaultAccountCredentialsProvider(
      AccountCredentialsRepository repository,
      CompositeCredentialsRepository<AccountCredentials<?>> compositeRepository) {
    this.repository = repository;
    this.compositeRepository = compositeRepository;
  }

  @Override
  public Set<AccountCredentials<?>> getAll() {
    Set<AccountCredentials<?>> all = (Set<AccountCredentials<?>>) repository.getAll();
    all.addAll(compositeRepository.getAllCredentials());
    return all;
  }

  @Override
  public AccountCredentials<?> getCredentials(String name) {
    AccountCredentials<?> credentials = repository.getOne(name);
    if (credentials != null) {
      return credentials;
    }

    return compositeRepository.getFirstCredentialsWithName(name);
  }
}
