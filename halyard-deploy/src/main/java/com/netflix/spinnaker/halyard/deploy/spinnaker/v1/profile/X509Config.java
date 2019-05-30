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

import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import com.netflix.spinnaker.halyard.config.model.v1.security.X509;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.commons.lang3.StringUtils;

@EqualsAndHashCode(callSuper = false)
@Data
public class X509Config {

  boolean enabled;

  String roleOid;
  String subjectPrincipalRegex;

  public X509Config(Security security) {
    if (!security.getAuthn().getX509().isEnabled()) {
      return;
    }

    X509 x509 = security.getAuthn().getX509();

    this.enabled = x509.isEnabled();
    if (StringUtils.isNotEmpty(x509.getRoleOid())) {
      this.roleOid = x509.getRoleOid();
    }
    if (StringUtils.isNotEmpty(x509.getNodeName())) {
      this.subjectPrincipalRegex = x509.getSubjectPrincipalRegex();
    }
  }
}
