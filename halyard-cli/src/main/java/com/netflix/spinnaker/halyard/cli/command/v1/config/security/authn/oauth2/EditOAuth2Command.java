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
 */

package com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.oauth2;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.netflix.spinnaker.halyard.cli.command.v1.config.security.authn.AbstractEditAuthnMethodCommand;
import com.netflix.spinnaker.halyard.cli.command.v1.converter.OAuth2ProviderTypeConverter;
import com.netflix.spinnaker.halyard.config.model.v1.security.AuthnMethod;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import lombok.Getter;

@Parameters(separators = "=")
public class EditOAuth2Command extends AbstractEditAuthnMethodCommand<OAuth2> {
  @Getter
  private AuthnMethod.Method method = AuthnMethod.Method.OAuth2;

  @Parameter(
      names = "--client-id",
      description = "The OAuth client ID you have configured with your OAuth provider."
  )
  private String clientId;

  @Parameter(
      names = "--client-secret",
      description = "The OAuth client secret you have configured with your OAuth provider."
  )
  private String clientSecret;

  @Parameter(
      names = "--provider",
      description = "The OAuth provider handling authentication. The supported options are Google, GitHub, and Azure",
      converter = OAuth2ProviderTypeConverter.class
  )
  private OAuth2.Provider provider;

  @Parameter(
      names = "--pre-established-redirect-uri",
      description = "The externally accessible URL for Gate. For use with load balancers that " +
          "do any kind of address manipulation for Gate traffic, such as an SSL terminating load " +
          "balancer."
  )
  private String preEstablishedRedirectUri;

  @DynamicParameter(
      names = "--user-info-requirements",
      description = "The map of requirements the userInfo request must have. This is used to " +
          "restrict user login to specific domains or having a specific attribute. Use equal " +
          "signs between key and value, and additional key/value pairs need to repeat the " +
          "flag. Example: '--user-info-requirements foo=bar --userInfoRequirements baz=qux'."
  )
  private OAuth2.UserInfoRequirements userInfoRequirements = new OAuth2.UserInfoRequirements();

  @Override
  protected AuthnMethod editAuthnMethod(OAuth2 authnMethod) {
    OAuth2.Client client = authnMethod.getClient();
    client.setClientId(isSet(clientId) ? clientId : client.getClientId());
    client.setClientSecret(isSet(clientSecret) ? clientSecret : client.getClientSecret());

    if (isSet(preEstablishedRedirectUri)) {
      if (preEstablishedRedirectUri.isEmpty()) {
        client.setPreEstablishedRedirectUri(null);
        client.setUseCurrentUri(null);
      } else {
        client.setPreEstablishedRedirectUri(preEstablishedRedirectUri);
        client.setUseCurrentUri(false);
      }
    }

    authnMethod.setProvider(provider != null ? provider : authnMethod.getProvider());

    if (!userInfoRequirements.isEmpty()) {
      authnMethod.setUserInfoRequirements(userInfoRequirements);
    }

    return authnMethod;
  }
}
