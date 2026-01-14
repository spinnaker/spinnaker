/*
 * Copyright 2026 Wise, PLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.gate.security.oauth2;

import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * An {@link OAuth2UserRequest} implementation for externally provided access tokens.
 *
 * <p>This class allows the creation of an OAuth2UserRequest without going through the standard
 * OAuth2 authorization code flow, enabling authentication with externally issued tokens such as
 * GitHub personal access tokens.
 */
public class ExternalOAuth2UserRequest extends OAuth2UserRequest {

  public ExternalOAuth2UserRequest(
      ClientRegistration clientRegistration, OAuth2AccessToken accessToken) {
    super(clientRegistration, accessToken);
  }
}
