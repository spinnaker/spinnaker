/*
 * Copyright 2021 Apple Inc.
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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinition;
import com.netflix.spinnaker.credentials.definition.CredentialsDefinitionSource;
import com.netflix.spinnaker.kork.annotations.NonnullByDefault;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides a full list of CredentialsDefinition account instances for a given credentials type.
 * Given an {@link AccountDefinitionRepository} bean and an optional list of {@code
 * CredentialsDefinitionSource<T>} beans for a given account type {@code T}, this class combines the
 * lists from all the given credentials definition sources. When no {@code
 * CredentialsDefinitionSource<T>} beans are available for a given account type, then a default
 * source should be specified to wrap any existing Spring configuration beans that provide the same.
 *
 * @param <T> account credentials definition type
 */
@NonnullByDefault
public class AccountDefinitionSource<T extends CredentialsDefinition>
    implements CredentialsDefinitionSource<T> {

  private static final Logger LOGGER = LogManager.getLogger();
  private final List<CredentialsDefinitionSource<T>> sources;

  /**
   * Constructs an account-based {@code CredentialsDefinitionSource<T>} using the provided
   * repository, account type, and additional sources for accounts of the same type.
   *
   * @param repository the backing repository for managing account definitions at runtime
   * @param type the account type supported by this source (must be annotated with {@link
   *     JsonTypeName})
   * @param additionalSources the list of other credential definition sources to list accounts from
   */
  public AccountDefinitionSource(
      AccountDefinitionRepository repository,
      Class<T> type,
      List<CredentialsDefinitionSource<T>> additionalSources) {
    String typeName = AccountDefinitionTypes.getCredentialsTypeName(type);
    Objects.requireNonNull(
        typeName, () -> "Class " + type + " is not annotated with type discriminator");
    List<CredentialsDefinitionSource<T>> sources = new ArrayList<>(additionalSources.size() + 1);
    sources.add(
        () ->
            repository.listByType(typeName).stream().map(type::cast).collect(Collectors.toList()));
    sources.addAll(additionalSources);
    this.sources = List.copyOf(sources);
  }

  @Override
  public List<T> getCredentialsDefinitions() {
    Set<String> seenAccountNames = new HashSet<>();
    return sources.stream()
        .flatMap(source -> source.getCredentialsDefinitions().stream())
        .filter(
            definition -> {
              var name = definition.getName();
              if (seenAccountNames.add(name)) {
                return true;
              } else {
                LOGGER.warn(
                    "Duplicate account name detected ({}). Skipping this definition.", name);
                return false;
              }
            })
        .collect(Collectors.toList());
  }
}
