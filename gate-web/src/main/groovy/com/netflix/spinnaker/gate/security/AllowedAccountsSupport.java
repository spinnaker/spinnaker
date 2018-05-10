package com.netflix.spinnaker.gate.security;

import com.netflix.spinnaker.fiat.model.Authorization;
import com.netflix.spinnaker.fiat.model.resources.Account;
import com.netflix.spinnaker.fiat.shared.FiatPermissionEvaluator;
import com.netflix.spinnaker.gate.services.CredentialsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Returns list of WRITE enabled accounts to populate X-SPINNAKER-ACCOUNTS header.
 */
@Component
public class AllowedAccountsSupport {

  private final FiatPermissionEvaluator fiatPermissionEvaluator;

  private final CredentialsService credentialsService;

  private final boolean fiatEnabled;

  @Autowired
  public AllowedAccountsSupport(FiatPermissionEvaluator fiatPermissionEvaluator,
                                CredentialsService credentialsService,
                                @Value("${services.fiat.enabled:false}") boolean fiatEnabled) {
    this.fiatPermissionEvaluator = fiatPermissionEvaluator;
    this.credentialsService = credentialsService;
    this.fiatEnabled = fiatEnabled;
  }

  public Collection<String> filterAllowedAccounts(String username, Collection<String> roles) {
    if (fiatEnabled) {
      return fiatPermissionEvaluator.getPermission(username).getAccounts()
        .stream()
        .filter(v -> v.getAuthorizations().contains(Authorization.WRITE))
        .map(Account.View::getName)
        .collect(Collectors.toSet());
    }

    return credentialsService.getAccountNames(roles);
  }
}

