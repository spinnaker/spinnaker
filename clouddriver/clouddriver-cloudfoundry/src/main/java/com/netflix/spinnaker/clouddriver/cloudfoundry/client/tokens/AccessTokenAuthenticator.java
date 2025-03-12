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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.Token;
import java.io.IOException;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Authenticator;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Route;
import org.jetbrains.annotations.Nullable;

@Getter
@RequiredArgsConstructor
public class AccessTokenAuthenticator implements Authenticator {

  private final AccessTokenProvider accessTokenProvider;

  @Nullable
  @Override
  public Request authenticate(@Nullable Route route, Response response) throws IOException {
    final Token currentToken = accessTokenProvider.getAccessToken();

    synchronized (accessTokenProvider.getTokenLock()) {
      final Token newToken = accessTokenProvider.getAccessToken();

      // Token was refreshed before the synchronization. Use the updated token and retry.
      if (!currentToken.equals(newToken)) {
        return newRequestWithAccessToken(response.request(), newToken);
      }

      // Refresh for new token and retry.
      accessTokenProvider.refreshAccessToken();
      final Token updatedToken = accessTokenProvider.getAccessToken();
      return newRequestWithAccessToken(response.request(), updatedToken);
    }
  }

  @NonNull
  private Request newRequestWithAccessToken(@NonNull Request request, @NonNull Token token) {
    return request.newBuilder().header("Authorization", "Bearer " + token.getAccessToken()).build();
  }
}
