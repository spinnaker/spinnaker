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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.security.Ldap;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import java.net.URI;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public class LdapConfig {
  boolean enabled;

  URI url;
  String userDnPattern;
  String userSearchBase;
  String userSearchFilter;
  String managerDn;
  String managerPassword;

  public LdapConfig(Security security) {
    if (!security.getAuthn().getLdap().isEnabled()) {
      return;
    }

    Ldap ldap = security.getAuthn().getLdap();

    this.enabled = ldap.isEnabled();
    this.url = ldap.getUrl();
    this.userDnPattern = ldap.getUserDnPattern();
    this.userSearchBase = ldap.getUserSearchBase();
    this.userSearchFilter = ldap.getUserSearchFilter();
    this.managerDn = ldap.getManagerDn();
    this.managerPassword = ldap.getManagerPassword();
  }
}
