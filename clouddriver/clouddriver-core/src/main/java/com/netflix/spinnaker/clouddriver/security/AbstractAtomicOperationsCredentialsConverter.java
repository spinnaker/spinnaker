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
 *
 */

package com.netflix.spinnaker.clouddriver.security;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperationConverter;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.kork.web.exceptions.InvalidRequestException;
import jakarta.validation.constraints.NotNull;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractAtomicOperationsCredentialsConverter<T extends AccountCredentials<?>>
    implements AtomicOperationConverter {

  @Autowired @Getter @Setter private CredentialsRepository<T> credentialsRepository;

  @Getter
  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  @NotNull
  public T getCredentialsObject(@NotNull final String name) {
    T creds = credentialsRepository.getOne(name);
    if (creds == null) {
      throw new InvalidRequestException(
          String.format(
              "credentials not found (name: %s, names: %s)",
              name,
              credentialsRepository.getAll().stream()
                  .map(AccountCredentials::getName)
                  .collect(Collectors.joining(","))));
    }
    return creds;
  }
}
