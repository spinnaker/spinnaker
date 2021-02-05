package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.NonNull;

public abstract class BaseServiceAccountResourceProvider
    extends BaseResourceProvider<ServiceAccount> {
  private final Collection<ServiceAccountPredicateProvider> serviceAccountPredicateProviders;

  public BaseServiceAccountResourceProvider(
      Collection<ServiceAccountPredicateProvider> serviceAccountPredicateProviders) {
    this.serviceAccountPredicateProviders = serviceAccountPredicateProviders;
  }

  @Override
  public Set<ServiceAccount> getAllRestricted(
      @NonNull String userId, @NonNull Set<Role> userRoles, boolean isAdmin)
      throws ProviderException {
    List<String> userRoleNames = userRoles.stream().map(Role::getName).collect(Collectors.toList());
    return getAll().stream()
        .filter(svcAcct -> !svcAcct.getMemberOf().isEmpty())
        .filter(
            svcAcct ->
                serviceAccountPredicateProviders.stream()
                    .anyMatch(p -> p.get(userId, userRoleNames, isAdmin).test(svcAcct)))
        .collect(Collectors.toSet());
  }

  @Override
  public Set<ServiceAccount> getAllUnrestricted() throws ProviderException {
    return Collections.emptySet();
  }
}
