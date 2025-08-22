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
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Custom OAuth2 user service that extends {@link DefaultOAuth2UserService} to load and process
 * OAuth2 user details. This service integrates with {@link OAuthUserInfoServiceHelper} to transform
 * and return the Spinnaker-specific OAuth2 user object.
 *
 * <p>Overrides the {@link #loadUser(OAuth2UserRequest)} method to modify user details after
 * retrieving them from the OAuth2 provider.
 *
 * <p>Usage: This service is automatically registered as a Spring Bean and is used during OAuth2
 * authentication.
 *
 * @author rahul-chekuri
 * @see OAuthUserInfoServiceHelper
 */
@Service
@Slf4j
public class SpinnakerOAuth2UserInfoService extends DefaultOAuth2UserService {
  private OAuthUserInfoServiceHelper userInfoService;

  @Autowired
  public SpinnakerOAuth2UserInfoService(OAuthUserInfoServiceHelper userInfoService) {
    this.userInfoService = userInfoService;
  }

  @Override
  public OAuth2User loadUser(OAuth2UserRequest userRequest) {
    OAuth2User oAuth2User = super.loadUser(userRequest);
    return userInfoService.getOAuthSpinnakerUser(oAuth2User, userRequest);
  }
}
