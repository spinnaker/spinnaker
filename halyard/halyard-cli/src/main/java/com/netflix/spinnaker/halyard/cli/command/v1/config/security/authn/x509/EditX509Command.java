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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.x509;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.X509;
import lombok.Getter;

@Parameters(separators = "=")
public class EditX509Command extends AbstractEditAuthnMethodCommand<X509> {

  @Getter
  private String shortDescription =
      "Configure authentication and role information for a x509 authentication scheme";

  @Getter private AuthnMethod.Method method = AuthnMethod.Method.X509;

  @Getter
  private String longDescription =
      String.join(
          " ",
          "x509 authenticates users via client certificate and a corresponding private key",
          "These certificates optionally provide authorization information via custom Oids with",
          "corresponding group information for the user. This can be configured via --roleOid");

  @Parameter(
      names = "--role-oid",
      description =
          "The OID that encodes roles that the user specified in the x509 certificate"
              + " belongs to")
  private String roleOid;

  @Parameter(
      names = "--subject-principal-regex",
      description =
          "The regex used to parse the subject principal name embedded in the x509"
              + " certificate if necessary")
  private String subjectPrincipalRegex;

  @Override
  protected AuthnMethod editAuthnMethod(X509 x) {
    x.setRoleOid(isSet(roleOid) ? roleOid : x.getRoleOid());
    x.setSubjectPrincipalRegex(
        isSet(subjectPrincipalRegex) ? subjectPrincipalRegex : x.getSubjectPrincipalRegex());

    return x;
  }
}
