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

import com.google.common.collect.Sets;
import com.netflix.spinnaker.fiat.model.ServiceAccount;
import com.netflix.spinnaker.fiat.model.UserPermission;
import com.netflix.spinnaker.fiat.permissions.PermissionsRepository;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DefaultServiceAccountProvider implements ServiceAccountProvider, InitializingBean {

  @Autowired
  private Front50Service front50Service;

  @Autowired
  private PermissionsRepository permissionsRepo;

  @Override
  public void afterPropertiesSet() {
    front50Service
        .getAllServiceAccounts()
        .stream()
        .forEach(serviceAccount -> {
          // Can't resolve full account/application permissions here because it would cause
          // a dependency cycle. Instead, account/applications are resolved on the first
          // periodic sync.
          permissionsRepo.put(new UserPermission()
                                  .setId(serviceAccount.getName())
                                  .setServiceAccounts(Sets.newHashSet(serviceAccount)));
          log.info("Adding service account '{}' to permission repo", serviceAccount.getName());
        });
  }

  /**
   * Return the set of service accounts to which a user with the specified collection of groups
   * has access.
   *
   * Service accounts are usually defined using a full email address, but the specified groups are
   * normally just the first part before the "@" symbol. This implementation strips everything
   * after the "@" symbol for the purposes of service account/group matching.
   */
  @Override
  public Set<ServiceAccount> getAccounts(@NonNull Collection<String> groups) {
    // There is a potential here for a naming collision where service account
    // "my-svc-account@abc.com" and "my-svc-account@xyz.com" each allow one another's users to use
    // their service account. In practice, though, I don't think this will be an issue.
    Map<String, ServiceAccount> serviceAccountsByName = front50Service
        .getAllServiceAccounts()
        .stream()
        .collect(Collectors.toMap(ServiceAccount::getNameWithoutDomain, Function.identity()));

    return groups
        .stream()
        .filter(serviceAccountsByName::containsKey)
        .map(serviceAccountsByName::get)
        .collect(Collectors.toSet());
  }
}
