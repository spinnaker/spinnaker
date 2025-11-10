/*
 * Copyright 2025 Razorpay.
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

package com.netflix.spinnaker.fiat.roles.github.client;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

@Slf4j
@RequiredArgsConstructor
public class GitHubAppRequestInterceptor implements Interceptor {

  private final GitHubAppAuthService authService;

  @Override
  public @NotNull Response intercept(Chain chain) throws IOException {
    try {
      String installationToken = authService.getInstallationToken();

      Request request =
          chain
              .request()
              .newBuilder()
              .addHeader("Authorization", "Bearer " + installationToken)
              .addHeader("Accept", "application/vnd.github.v3+json")
              .addHeader("User-Agent", "Spinnaker-Fiat")
              .build();

      return chain.proceed(request);
    } catch (Exception e) {
      log.error("Failed to authenticate GitHub App request", e);
      throw new IOException("GitHub App authentication failed", e);
    }
  }
}
