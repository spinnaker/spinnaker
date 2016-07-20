/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.ServiceAccount;
import lombok.NonNull;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

// TODO(ttomsu): Pull this feature config into Front50.
@Component
public class DefaultServiceAccountProvider implements ServiceAccountProvider {

  @Setter
  private Map<String, ServiceAccount> serviceAccountsByName;

  @Override
  public Optional<ServiceAccount> getAccount(@NonNull String name) {
    return Optional.ofNullable(serviceAccountsByName.get(name));
  }

  /**
   * Return the set of service accounts to which a user with the specified collection of groups
   * has access.
   */
  @Override
  public Set<ServiceAccount> getAccounts(@NonNull Collection<String> groups) {
    if (serviceAccountsByName == null) {
      return Collections.emptySet();
    }

    return groups
        .stream()
        .filter(group -> serviceAccountsByName.containsKey(group))
        .map(group -> serviceAccountsByName.get(group))
        .collect(Collectors.toSet());
  }
}
