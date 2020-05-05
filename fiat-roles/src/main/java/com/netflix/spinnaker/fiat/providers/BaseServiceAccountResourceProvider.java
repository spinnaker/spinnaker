package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.config.FiatRoleConfig;
import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.NonNull;

public abstract class BaseServiceAccountResourceProvider
    extends BaseResourceProvider<ServiceAccount> {
  private final FiatRoleConfig fiatRoleConfig;

  public BaseServiceAccountResourceProvider(FiatRoleConfig fiatRoleConfig) {
    this.fiatRoleConfig = fiatRoleConfig;
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
