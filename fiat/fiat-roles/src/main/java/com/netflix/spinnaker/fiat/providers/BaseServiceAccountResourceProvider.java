package com.netflix.spinnaker.fiat.providers;

import com.netflix.spinnaker.fiat.model.resources.Role;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
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
    Set<String> userRoleNames = userRoles.stream().map(Role::getName).collect(Collectors.toSet());
    List<ServiceAccountPredicateProvider> providers = List.copyOf(serviceAccountPredicateProviders);
    // Built lazily so a provider is only asked when an earlier one hasn't already granted access.
    List<Predicate<ServiceAccount>> predicates =
        new ArrayList<>(Collections.nCopies(providers.size(), null));
    return getAll().stream()
        .filter(svcAcct -> !svcAcct.getMemberOf().isEmpty())
        .filter(
            svcAcct -> {
              for (int i = 0; i < providers.size(); i++) {
                Predicate<ServiceAccount> predicate = predicates.get(i);
                if (predicate == null) {
                  predicate = providers.get(i).get(userId, userRoleNames, isAdmin);
                  predicates.set(i, predicate);
                }
                if (predicate.test(svcAcct)) {
                  return true;
                }
              }
              return false;
            })
        .collect(Collectors.toSet());
  }

  @Override
  public Set<ServiceAccount> getAllUnrestricted() throws ProviderException {
    return Collections.emptySet();
  }
}
