/*
 * Copyright 2025 OpsMx, Inc.
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

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

/**
 * Custom OIDC user service that extends {@link OidcUserService} to load and process OIDC user
 * details. This service integrates with {@link OAuthUserInfoServiceHelper} to transform and return
 * the Spinnaker-specific OIDC user object.
 *
 * <p>Overrides the {@link #loadUser(OidcUserRequest)} method to modify user details after
 * retrieving them from the OpenID Connect (OIDC) provider.
 *
 * @author rahul-chekuri
 * @see OAuthUserInfoServiceHelper
 */
@Service
@Slf4j
public class SpinnakerOIDCUserInfoService extends OidcUserService {
  private OAuthUserInfoServiceHelper userInfoService;

  @Autowired
  public SpinnakerOIDCUserInfoService(OAuthUserInfoServiceHelper userInfoService) {
    this.userInfoService = userInfoService;
  }

  @Override
  public OidcUser loadUser(OidcUserRequest userRequest) {
    OidcUser oidcUser = super.loadUser(userRequest);
    return userInfoService.getOAuthSpinnakerUser(oidcUser, userRequest);
  }
}
