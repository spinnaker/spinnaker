/*
 * Copyright 2021 Netflix, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.tokens;

import static com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryClientUtils.safelyCall;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.CloudFoundryApiException;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.api.AuthenticationService;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.Token;
import java.util.concurrent.locks.ReentrantLock;
import lombok.Getter;

public class AccessTokenProvider {

  private final String user;
  private final String password;
  private final AuthenticationService uaa;
  @Getter private final Object tokenLock = new Object();
  @Getter private long tokenExpiration;
  private Token token;
  @Getter private ReentrantLock refreshLock = new ReentrantLock();

  public AccessTokenProvider(String user, String password, AuthenticationService uaa) {
    this.user = user;
    this.password = password;
    this.uaa = uaa;
  }

  Token getAccessToken() {
    if (token == null) {
      refreshAccessToken();
    }
    return this.token;
  }

  void refreshAccessToken() {
    try {
      Token token =
          safelyCall(() -> uaa.passwordToken("password", user, password, "cf", ""))
              .orElseThrow(
                  () ->
                      new CloudFoundryApiException(
                          "Unable to get authentication token from cloud foundry."));
      this.token = token;
      this.tokenExpiration = System.currentTimeMillis() + ((token.getExpiresIn() - 120) * 1000);
    } catch (Exception e) {
      throw new CloudFoundryApiException(e, "Could not refresh token.");
    }
  }
}
