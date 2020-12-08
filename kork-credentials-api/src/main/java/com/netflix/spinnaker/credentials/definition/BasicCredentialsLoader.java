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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;

/**
 * CredentialsLoader that expects the full list of credentials on each load, and updates the
 * credential repository on each run. It can be run once or multiple times.
 *
 * @param <T>
 * @param <U>
 */
public class BasicCredentialsLoader<T extends CredentialsDefinition, U extends Credentials>
    extends AbstractCredentialsLoader<U> {
  protected final CredentialsParser<T, U> parser;
  protected final CredentialsDefinitionSource<T> definitionSource;
  /**
   * When parallel is true, the loader may apply changes in parallel. See {@link
   * java.util.concurrent.ForkJoinPool} for limitations. This can be useful when adding or updating
   * credentials is expected to take some time, as for instance when making a network call.
   */
  @Setter @Getter protected boolean parallel;
  // Definition is kept so we can quickly check for changes before parsing
  protected final Map<String, T> loadedDefinitions = new ConcurrentHashMap<>();

  public BasicCredentialsLoader(
      CredentialsDefinitionSource<T> definitionSource,
      CredentialsParser<T, U> parser,
      CredentialsRepository<U> credentialsRepository) {
    this(definitionSource, parser, credentialsRepository, false);
  }

  public BasicCredentialsLoader(
      CredentialsDefinitionSource<T> definitionSource,
      CredentialsParser<T, U> parser,
      CredentialsRepository<U> credentialsRepository,
      boolean parallel) {
    super(credentialsRepository);
    this.parser = parser;
    this.definitionSource = definitionSource;
    this.parallel = parallel;
  }

  @Override
  public void load() {
    this.parse(definitionSource.getCredentialsDefinitions());
  }

  protected void parse(Collection<T> definitions) {
    Set<String> definitionNames = definitions.stream().map(T::getName).collect(Collectors.toSet());

    credentialsRepository.getAll().stream()
        .map(Credentials::getName)
        .filter(name -> !definitionNames.contains(name))
        .peek(loadedDefinitions::remove)
        .forEach(credentialsRepository::delete);

    List<U> toApply = new ArrayList<>();

    for (T definition : definitions) {
      if (!loadedDefinitions.containsKey(definition.getName())) {
        U cred = parser.parse(definition);
        if (cred != null) {
          toApply.add(cred);
          // Add to loaded definition now in case we trigger another parse before this one finishes
          loadedDefinitions.put(definition.getName(), definition);
        }
      } else if (!loadedDefinitions.get(definition.getName()).equals(definition)) {
        U cred = parser.parse(definition);
        if (cred != null) {
          toApply.add(cred);
          loadedDefinitions.put(definition.getName(), definition);
        }
      }
    }

    Stream<U> stream = parallel ? toApply.parallelStream() : toApply.stream();
    stream.forEach(credentialsRepository::save);
  }
}
