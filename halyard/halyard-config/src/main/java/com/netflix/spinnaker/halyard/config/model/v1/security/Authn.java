/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.security;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Data
@EqualsAndHashCode(callSuper = true)
public class Authn extends Node {
  @Getter private String nodeName = "authn";

  private OAuth2 oauth2 = new OAuth2();
  private Saml saml = new Saml();
  private Ldap ldap = new Ldap();
  private X509 x509 = new X509();
  private IAP iap = new IAP();
  private boolean enabled;

  public boolean isEnabled() {
    return getOauth2().isEnabled()
        || getSaml().isEnabled()
        || getLdap().isEnabled()
        || getX509().isEnabled()
        || getIap().isEnabled();
  }

  public void setEnabled(boolean _ignored) {}
}
