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

import com.netflix.spinnaker.fiat.config.FiatRoleConfig;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import com.netflix.spinnaker.fiat.providers.internal.Front50Service;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DefaultServiceAccountProvider extends BaseProvider<ServiceAccount>
    implements ResourceProvider<ServiceAccount> {

  private final Front50Service front50Service;

  private final FiatRoleConfig fiatRoleConfig;

  @Autowired
  public DefaultServiceAccountProvider(
      Front50Service front50Service, FiatRoleConfig fiatRoleConfig) {
    super();
    this.front50Service = front50Service;
    this.fiatRoleConfig = fiatRoleConfig;
  }

  @Override
  protected Set<ServiceAccount> loadAll() throws ProviderException {
    try {
      return new HashSet<>(front50Service.getAllServiceAccounts());
    } catch (Exception e) {
      throw new ProviderException(this.getClass(), e.getCause());
    }
  }

  @Override
  public Set<ServiceAccount> getAllRestricted(@NonNull Set<Role> roles, boolean isAdmin)
      throws ProviderException {
    List<String> roleNames = roles.stream().map(Role::getName).collect(Collectors.toList());
    return getAll().stream()
        .filter(svcAcct -> !svcAcct.getMemberOf().isEmpty())
        .filter(getServiceAccountPredicate(isAdmin, roleNames))
        .collect(Collectors.toSet());
  }

  private Predicate<ServiceAccount> getServiceAccountPredicate(
      boolean isAdmin, List<String> roleNames) {
    if (isAdmin) {
      return svcAcct -> true;
    }
    if (fiatRoleConfig.isOrMode()) {
      return svcAcct -> svcAcct.getMemberOf().stream().anyMatch(roleNames::contains);
    } else {
      return svcAcct -> roleNames.containsAll(svcAcct.getMemberOf());
    }
  }

  @Override
  public Set<ServiceAccount> getAllUnrestricted() throws ProviderException {
    return Collections.emptySet();
  }
}
