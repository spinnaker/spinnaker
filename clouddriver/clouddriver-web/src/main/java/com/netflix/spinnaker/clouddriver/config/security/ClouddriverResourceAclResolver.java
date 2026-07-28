/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.config.security;

import com.netflix.spinnaker.clouddriver.security.AbstractAccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
import com.netflix.spinnaker.security.authz.Permissions;
import com.netflix.spinnaker.security.authz.ResourceAclResolver;
import com.netflix.spinnaker.security.authz.ResourceType;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owner-local {@link ResourceAclResolver} for Clouddriver. Clouddriver is the authoritative owner
 * of {@code account} ACLs, so it resolves them straight from its own in-process {@link
 * AccountCredentialsProvider} — each account's embedded {@link Permissions} — never from a remote
 * or cached copy of a permission graph.
 *
 * <p>This backs the {@code hasPermission(authentication, accountName, "ACCOUNT", ...)} call path
 * used by the {@code @PreAuthorize}/{@code @PostFilter} annotations and the role-filtered {@code
 * GET /credentials} listing, where only the account name is available.
 *
 * <p>Returning {@code null} signals "no ACL resolvable here" (unknown account); the enclosing
 * evaluator then applies its unknown-resource fallback. Non-account resource types (e.g. {@code
 * application}) are not owned by Clouddriver and resolve to {@code null}.
 */
public class ClouddriverResourceAclResolver implements ResourceAclResolver {

  private static final Logger log = LoggerFactory.getLogger(ClouddriverResourceAclResolver.class);

  private final AccountCredentialsProvider accountCredentialsProvider;

  public ClouddriverResourceAclResolver(AccountCredentialsProvider accountCredentialsProvider) {
    this.accountCredentialsProvider = accountCredentialsProvider;
  }

  @Nullable
  @Override
  public Permissions resolve(ResourceType resourceType, String resourceName) {
    if (resourceName == null || !ResourceType.ACCOUNT.equals(resourceType)) {
      return null;
    }
    try {
      AccountCredentials<?> credentials = accountCredentialsProvider.getCredentials(resourceName);
      if (credentials == null) {
        // Unknown account: no ACL resolvable here.
        return null;
      }
      if (credentials instanceof AbstractAccountCredentials) {
        return ((AbstractAccountCredentials<?>) credentials).getPermissions();
      }
      // An existing account whose credential type carries no embedded ACL is unrestricted.
      return Permissions.EMPTY;
    } catch (Exception e) {
      log.debug("Unable to resolve account ACL for '{}'", resourceName, e);
      return null;
    }
  }
}
