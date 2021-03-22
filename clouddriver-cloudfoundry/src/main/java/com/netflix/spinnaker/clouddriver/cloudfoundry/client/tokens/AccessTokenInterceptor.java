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
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

@RequiredArgsConstructor
public class AccessTokenInterceptor implements Interceptor {

  private final AccessTokenProvider accessTokenProvider;

  @Override
  public Response intercept(Chain chain) throws IOException {
    final Token currentToken = accessTokenProvider.getAccessToken();
    // Token is expiring soon, refresh and proceed.
    if (currentToken == null
        || System.currentTimeMillis() >= accessTokenProvider.getTokenExpiration()) {

      if (accessTokenProvider.getRefreshLock().tryLock()) {
        try {
          final Token newToken = accessTokenProvider.getAccessToken();

          // Token was refreshed before getting the lock. Use the updated token and proceed.
          if (currentToken == null || !currentToken.equals(newToken)) {
            return chain.proceed(newRequestWithAccessToken(chain.request(), newToken));
          }

          // Refresh for new token and proceed.
          accessTokenProvider.refreshAccessToken();

          final Token updatedToken = accessTokenProvider.getAccessToken();
          return chain.proceed(newRequestWithAccessToken(chain.request(), updatedToken));
        } finally {
          if (accessTokenProvider.getRefreshLock().isHeldByCurrentThread()) {
            accessTokenProvider.getRefreshLock().unlock();
          }
        }
      }
    }
    // Token should still be valid even though its expiring soon, proceed.
    return chain.proceed(newRequestWithAccessToken(chain.request(), currentToken));
  }

  @NonNull
  private Request newRequestWithAccessToken(@NonNull Request request, @NonNull Token token) {
    return request.newBuilder().header("Authorization", "Bearer " + token.getAccessToken()).build();
  }
}
