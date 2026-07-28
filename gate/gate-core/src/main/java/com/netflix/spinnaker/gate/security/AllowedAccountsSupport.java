/*
 * Copyright 2016 Netflix, Inc.
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.security;

import com.netflix.spinnaker.gate.services.CredentialsService;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Returns the list of WRITE-enabled accounts (used to populate the legacy {@code
 * X-SPINNAKER-ACCOUNTS} header) for the caller's roles.
 *
 * <p>Allowed accounts are derived from Clouddriver's own account listing ({@code GET /credentials},
 * surfaced via {@link CredentialsService}) filtered by the caller's roles. Clouddriver owns and
 * enforces account ACLs locally (a sibling change makes its listing role-filtered for the caller),
 * so this header is now a best-effort hint; the authoritative account-authorization decision
 * happens owner-locally in Clouddriver against the caller's signed identity token.
 */
@Component
public class AllowedAccountsSupport {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private final CredentialsService credentialsService;

  @Autowired
  public AllowedAccountsSupport(CredentialsService credentialsService) {
    this.credentialsService = credentialsService;
  }

  public Collection<String> filterAllowedAccounts(String username, Collection<String> roles) {
    // ignoreAuthStatus=true forces CredentialsService to filter Clouddriver's account permissions
    // against the caller's roles locally.
    Collection<String> allowedAccounts = credentialsService.getAccountNames(roles, true);
    log.debug(
        "Derived allowed accounts for user {} from Clouddriver account listing (roles: {}, allowedAccounts: {})",
        username,
        roles,
        allowedAccounts);
    return allowedAccounts;
  }
}
