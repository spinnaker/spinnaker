/*
 * Copyright 2020 Netflix, Inc.
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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Permissions;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// Todo: remove this class once these methods no longer need to be separated from AccountCredentials
public abstract class AbstractAccountCredentials<T> implements AccountCredentials<T> {

  // Todo: use jackson mixin on AccountCredentials rather than putting annotation here
  @JsonIgnore
  public abstract T getCredentials();

  // Todo: make Fiat an acceptable dependency for clouddriver-api and push up to AccountCredentials
  public Permissions getPermissions() {
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
}
