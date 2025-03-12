/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.iap;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod.Method;
import com.netflix.spinnaker.halyard.config.model.v1.security.IAP;
import lombok.Getter;

@Parameters(separators = "=")
public class EditIAPCommand extends AbstractEditAuthnMethodCommand<IAP> {

  @Getter
  private String shortDescription =
      "Configure authentication using the Google Cloud Identity-Aware "
          + "Proxy authentication model.";

  @Getter private AuthnMethod.Method method = Method.IAP;

  @Getter
  private String longDescription =
      "Google Cloud Identity-Aware Proxy (IAP) is an authentication "
          + "model that utilizes Google OAuth2.0 and an authorization service to provide access control "
          + "for users of GCP. After a user has been authenticated and authorized by IAP's service, "
          + "a JWT token is passed along which Spinnaker uses to check for authenticity and to get "
          + "the user email from the payload and sign the user in. To configure IAP, set the audience field"
          + " retrieved from the IAP console.";

  @Parameter(
      names = "--audience",
      description =
          "The Audience from the ID token payload. You can retrieve this field from the"
              + " IAP console: https://cloud.google.com/iap/docs/signed-headers-howto#verify_the_id_token_header.")
  private String audience;

  @Parameter(
      names = "--jwt-header",
      description = "The HTTP request header that contains the JWT token.")
  private String jwtHeader;

  @Parameter(names = "--issuer-id", description = "The Issuer from the ID token payload.")
  private String issuerId;

  @Parameter(
      names = "--iap-verify-key-url",
      description = "The URL containing the Cloud IAP public keys in JWK format.")
  private String iapVerifyKeyUrl;

  @Override
  protected AuthnMethod editAuthnMethod(IAP iap) {
    iap.setAudience(isSet(audience) ? audience : iap.getAudience());
    iap.setJwtHeader(isSet(jwtHeader) ? jwtHeader : iap.getJwtHeader());
    iap.setIssuerId(isSet(issuerId) ? issuerId : iap.getIssuerId());
    iap.setIapVerifyKeyUrl(isSet(iapVerifyKeyUrl) ? iapVerifyKeyUrl : iap.getIapVerifyKeyUrl());

    return iap;
  }
}
