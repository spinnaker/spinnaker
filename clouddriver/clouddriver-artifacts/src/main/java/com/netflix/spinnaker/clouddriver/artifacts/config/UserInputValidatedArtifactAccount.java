/*
 * Copyright 2025 Harness, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.artifacts.config;

import com.netflix.spinnaker.fiat.model.resources.Permissions;
import com.netflix.spinnaker.kork.web.url.UrlRestrictions;
import com.netflix.spinnaker.kork.web.url.UrlRestrictionsProperties;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import lombok.Getter;

@Getter
public abstract class UserInputValidatedArtifactAccount implements ArtifactAccount {
  private final String name;

  /**
   * This is used in cases where user input is allowed for the URL. Specific github file references,
   * and simple http references. NOT all accounts need this as many define the access IN the
   * configuration file itself (e.g. dockerhub).
   */
  @Nullable private final UrlRestrictions urlRestrictions;

  private final Permissions.Builder permissions;

  /**
   * @param urlRestrictions restrictions for user-supplied URLs, or null for accounts whose URLs
   *     come only from their own configuration
   * @param permissions Fiat permissions for the account, or null for none
   */
  protected UserInputValidatedArtifactAccount(
      String name,
      @Nullable UrlRestrictionsProperties urlRestrictions,
      @Nullable Permissions.Builder permissions) {
    this.name = name;
    this.urlRestrictions = urlRestrictions == null ? null : urlRestrictions.toUrlRestrictions();
    this.permissions = permissions == null ? new Permissions.Builder() : permissions;
  }

  /**
   * Whether the account is configured to send credentials with its requests. Checked without
   * reading any credential files so it is cheap to call while configuring the account.
   */
  public boolean hasCredentials() {
    if (this instanceof BasicAuth) {
      BasicAuth basicAuth = (BasicAuth) this;
      if (basicAuth.getUsernamePasswordFile().isPresent()
          || (basicAuth.getUsername().isPresent() && basicAuth.getPassword().isPresent())) {
        return true;
      }
    }
    if (this instanceof TokenAuth) {
      TokenAuth tokenAuth = (TokenAuth) this;
      return tokenAuth.getToken().isPresent() || tokenAuth.getTokenFile().isPresent();
    }
    return false;
  }

  /**
   * Restrictions as configured, except that when the account has credentials and no {@code
   * allowedDomains} are configured, {@code defaultAllowedDomains} are used. Accounts for a service
   * with a well known host use this so they work without configuration on the hosted service, while
   * a self-hosted server has to say where it is.
   */
  protected static UrlRestrictionsProperties orDefault(
      @Nullable UrlRestrictionsProperties urlRestrictions,
      boolean hasCredentials,
      List<String> defaultAllowedDomains) {
    UrlRestrictionsProperties restrictions = orDefault(urlRestrictions);
    if (hasCredentials && restrictions.getAllowedDomains().isEmpty()) {
      restrictions.setAllowedDomains(new ArrayList<>(defaultAllowedDomains));
    }
    return restrictions;
  }

  /** Account restrictions as configured, or the defaults when none are configured. */
  protected static UrlRestrictionsProperties orDefault(
      @Nullable UrlRestrictionsProperties urlRestrictions) {
    return urlRestrictions == null ? new UrlRestrictionsProperties() : urlRestrictions;
  }
}
