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

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.profile;

import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2;
import com.netflix.spinnaker.halyard.config.model.v1.security.OAuth2.UserInfoMapping;
import com.netflix.spinnaker.halyard.config.model.v1.security.Security;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
class SpringConfig {
  OAuth2 oauth2;

  OAuth2Security security;

  SpringConfig(Security security) {
    OAuth2 oauth2 = security.getAuthn().getOauth2();
    if (oauth2.isEnabled()) {
      this.oauth2 = oauth2;
    }
  }

  SpringConfig(OAuth2 oauth2) {
    if (oauth2.isEnabled()) {
      this.security = populateOAuth2Security(oauth2);
    }
  }

  private OAuth2Security populateOAuth2Security(OAuth2 oauth2) {
    OAuth2.Provider provider = oauth2.getProvider();
    OAuth2Security.OAuth2.Client client = new OAuth2Security.OAuth2.Client();

    Map<String, String> registration = new HashMap<>();
    Map<String, String> prvdr = new HashMap<>();

    switch (provider) {
      case GOOGLE:
        client.getProvider().setGoogle(prvdr);
        client.getRegistration().setGoogle(registration);
        break;
      case GITHUB:
        client.getProvider().setGithub(prvdr);
        client.getRegistration().setGithub(registration);
        break;
      case ORACLE:
        client.getProvider().setOracle(prvdr);
        client.getRegistration().setOracle(registration);
        break;
      case AZURE:
        client.getProvider().setAzure(prvdr);
        client.getRegistration().setAzure(registration);
        break;
      case OTHER:
        client.getProvider().setOther(prvdr);
        client.getRegistration().setOther(registration);
        break;
      default:
        throw new RuntimeException("Unknown provider type " + provider);
    }

    registration.put("client-id", oauth2.getClient().getClientId());
    registration.put("client-secret", oauth2.getClient().getClientSecret());
    registration.put("scope", oauth2.getClient().getScope());
    registration.put(
        "clientAuthenticationScheme", oauth2.getClient().getClientAuthenticationScheme());
    prvdr.put("token-uri", oauth2.getClient().getAccessTokenUri());
    prvdr.put("authorization-uri", oauth2.getClient().getUserAuthorizationUri());
    prvdr.put("user-info-uri", oauth2.getResource().getUserInfoUri());
    client.getRegistration().setUserInfoMapping(oauth2.getUserInfoMapping());
    client.getRegistration().setUserInfoRequirements(oauth2.getUserInfoRequirements());

    OAuth2Security security = new OAuth2Security();
    security.getOauth2().setClient(client);
    return security;
  }

  @Data
  private class OAuth2Security {
    private OAuth2 oauth2 = new OAuth2();

    @Data
    private class OAuth2 {
      private Client client = new Client();

      @Data
      public static class Client {
        private Registration registration = new Registration();
        private Provider provider = new Provider();
      }

      @Data
      public static class Registration {
        private UserInfoMapping userInfoMapping;
        private Map<String, String> userInfoRequirements;
        private Map<String, String> google;
        private Map<String, String> github;
        private Map<String, String> azure;
        private Map<String, String> oracle;
        private Map<String, String> other;
      }

      @Data
      public static class Provider {
        private Map<String, String> google;
        private Map<String, String> github;
        private Map<String, String> azure;
        private Map<String, String> oracle;
        private Map<String, String> other;
      }
    }
  }
}
