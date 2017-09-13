/*
 * Copyright 2017 Target, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.ldap;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.URIConverter;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap;
import lombok.Getter;

import java.net.URI;

public class EditLdapCommand extends AbstractEditAuthnMethodCommand<Ldap> {

  @Getter
  private String shortDescription = "Configure authentication using a LDAP identity provider.";

  @Getter
  private String longDescription = String.join(" ", "Lightweight Directory Access Protocol (LDAP)",
      "is a standard way many organizations maintain user credentials and group memberships.",
      "Spinnaker uses the standard “bind” approach for user authentication.",
      "This is a fancy way of saying that Gate uses your username and password to login to the LDAP server,",
      "and if the connection is successful, you’re considered authenticated.");

  @Getter
  private AuthnMethod.Method method = AuthnMethod.Method.LDAP;

  @Parameter(
      names = "--url",
      description = "ldap:// or ldaps:// url of the LDAP server",
      converter = URIConverter.class
  )
  private URI url;

  @Parameter(
      names = "--user-dn-pattern",
      description = "Placeholder...uid={0},ou=users"
  )
  private String userDnPattern;

  @Parameter(
      names = "--user-search-base",
      description = "Placeholder..."
  )
  private String userSearchBase;

  @Parameter(
      names = "--user-search-filter",
      description = "Placeholder"
  )
  private String userSearchFilter;

  @Override
  protected AuthnMethod editAuthnMethod(Ldap ldap) {
    ldap.setUrl(isSet(url) ? url : ldap.getUrl());
    ldap.setUserDnPattern(isSet(userDnPattern) ? userDnPattern : ldap.getUserDnPattern());
    ldap.setUserSearchBase(isSet(userSearchBase) ? userSearchBase : ldap.getUserSearchBase());
    ldap.setUserSearchFilter(isSet(userSearchFilter) ? userSearchFilter : ldap.getUserSearchFilter());

    if (isSet(userSearchBase)) {
      if (userSearchBase.isEmpty()) {
        ldap.setUserSearchBase(null);
      }
    }

    if (isSet(userSearchFilter)) {
      if (userSearchFilter.isEmpty()) {
        ldap.setUserSearchFilter(null);
      }
    }

    return ldap;
  }
}
