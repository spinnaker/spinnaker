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

import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIterator;
import com.netflix.spinnaker.halyard.config.model.v1.node.NodeIteratorFactory;
import com.netflix.spinnaker.halyard.config.model.v1.node.Secret;
import java.util.Arrays;
import java.util.HashMap;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class OAuth2 extends AuthnMethod {
  @Override
  public String getNodeName() {
    return "oauth2";
  }

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeEmptyIterator();
  }

  @Override
  public Method getMethod() {
    return Method.OAuth2;
  }

  private Client client = new Client();
  private UserInfoRequirements userInfoRequirements;
  private Resource resource = new Resource();
  private UserInfoMapping userInfoMapping = new UserInfoMapping();
  private Provider provider;

  public void setProvider(Provider provider) {
    this.provider = provider;

    if (provider == null) {
      return;
    }

    Client newClient =
        new Client()
            .setClientId(client.getClientId())
            .setClientSecret(client.getClientSecret())
            .setPreEstablishedRedirectUri(client.getPreEstablishedRedirectUri())
            .setUseCurrentUri(client.getUseCurrentUri());
    Resource newResource = new Resource();
    UserInfoMapping newUserInfoMapping = new UserInfoMapping();

    switch (provider) {
      case GOOGLE:
        newClient.setAccessTokenUri("https://www.googleapis.com/oauth2/v4/token");
        newClient.setUserAuthorizationUri("https://accounts.google.com/o/oauth2/v2/auth");
        newClient.setScope("profile email");

        newResource.setUserInfoUri("https://www.googleapis.com/oauth2/v3/userinfo");

        newUserInfoMapping.setEmail("email");
        newUserInfoMapping.setFirstName("given_name");
        newUserInfoMapping.setLastName("family_name");
        break;
      case GITHUB:
        newClient.setAccessTokenUri("https://github.com/login/oauth/access_token");
        newClient.setUserAuthorizationUri("https://github.com/login/oauth/authorize");
        newClient.setScope("user:email");

        newResource.setUserInfoUri("https://api.github.com/user");

        newUserInfoMapping.setEmail("email");
        newUserInfoMapping.setFirstName("");
        newUserInfoMapping.setLastName("name");
        newUserInfoMapping.setUsername("login");
        break;
      case ORACLE:
        final String idcsBaseUrl = "https://idcs-${idcsTenantId}.identity.oraclecloud.com";
        newClient.setAccessTokenUri(idcsBaseUrl + "/oauth2/v1/token");
        newClient.setUserAuthorizationUri(idcsBaseUrl + "/oauth2/v1/authorize");
        newClient.setScope("openid urn:opc:idm:__myscopes__");

        newResource.setUserInfoUri(idcsBaseUrl + "/oauth2/v1/userinfo");

        newUserInfoMapping.setEmail("");
        newUserInfoMapping.setFirstName("given_name");
        newUserInfoMapping.setLastName("family_name");
        newUserInfoMapping.setUsername("preferred_username");
        break;
      case AZURE:
        newClient.setAccessTokenUri(
            "https://login.microsoftonline.com/${azureTenantId}/oauth2/token");
        newClient.setUserAuthorizationUri(
            "https://login.microsoftonline.com/${azureTenantId}/oauth2/authorize?resource=https://graph.windows.net");
        newClient.setScope("profile");
        newClient.setClientAuthenticationScheme("query");

        newResource.setUserInfoUri("https://graph.windows.net/me?api-version=1.6");

        newUserInfoMapping.setEmail("userPrincipalName");
        newUserInfoMapping.setFirstName("givenName");
        newUserInfoMapping.setLastName("surname");
        break;
      case OTHER:
        newClient.setAccessTokenUri(client.getAccessTokenUri());
        newClient.setUserAuthorizationUri(client.getUserAuthorizationUri());
        newClient.setScope(client.getScope());
        newClient.setClientAuthenticationScheme(client.getClientAuthenticationScheme());

        newResource.setUserInfoUri(resource.getUserInfoUri());

        newUserInfoMapping.setEmail(userInfoMapping.getEmail());
        newUserInfoMapping.setFirstName(userInfoMapping.getFirstName());
        newUserInfoMapping.setLastName(userInfoMapping.getLastName());
        newUserInfoMapping.setUsername(userInfoMapping.getUsername());
        break;
      default:
        throw new RuntimeException("Unknown provider type " + provider);
    }

    this.client = newClient;
    this.resource = newResource;
    this.userInfoMapping = newUserInfoMapping;
  }

  @Data
  public static class Client {
    private String clientId;
    @Secret private String clientSecret;
    private String accessTokenUri;
    private String userAuthorizationUri;
    private String clientAuthenticationScheme;
    private String scope;
    private String preEstablishedRedirectUri;
    private Boolean useCurrentUri;
  }

  @Data
  public static class Resource {
    private String userInfoUri;
  }

  @Data
  public static class UserInfoMapping {
    private String email;
    private String firstName;
    private String lastName;
    private String username;
  }

  @Data
  @EqualsAndHashCode(callSuper = false)
  public static class UserInfoRequirements extends HashMap<String, String> {

    @Override
    public String toString() {
      return this.isEmpty() ? "(empty)" : super.toString();
    }
  }

  public enum Provider {
    AZURE("azure"),
    GITHUB("github"),
    ORACLE("oracle"),
    OTHER("other"),
    GOOGLE("google");

    private String id;

    Provider(String id) {
      this.id = id;
    }

    public static Provider fromString(String name) {
      for (Provider type : Provider.values()) {
        if (type.toString().equalsIgnoreCase(name)) {
          return type;
        }
      }

      throw new IllegalArgumentException(
          "Provider \""
              + name
              + "\" is not a valid choice. The options are: "
              + Arrays.toString(Provider.values()));
    }
  }
}
