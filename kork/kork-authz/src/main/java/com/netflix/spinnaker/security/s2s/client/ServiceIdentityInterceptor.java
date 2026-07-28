/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.security.s2s.client;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Presents this service's Kubernetes ServiceAccount token on outbound calls so the peer can
 * authenticate the caller — the sending half of the {@code k8s-sa-token} service-to-service
 * provider. Without it, endpoints annotated {@link
 * com.netflix.spinnaker.security.s2s.AllowServiceCallers} reject every call with 403 because no
 * caller identity ever reaches them.
 *
 * <h2>Attach this only to Spinnaker-internal clients</h2>
 *
 * <p>The token is a bearer credential for this service's identity, so it must never be sent to a
 * host outside the deployment. This is deliberately <b>not</b> registered as a global {@code
 * OkHttpClientCustomizer}: those apply to every OkHttp client in the process, including ones that
 * fetch artifacts from GitHub, S3, and other third parties. Instead, wire it explicitly onto the
 * clients that target another Spinnaker service, so the set of hosts that can ever receive the
 * credential is a reviewable list rather than a side effect of whatever URL a pipeline supplies.
 *
 * <p>An existing header on the request is left untouched, so an explicitly-set identity always wins
 * over the ambient pod identity.
 */
public class ServiceIdentityInterceptor implements Interceptor {

  private final ProjectedServiceAccountTokenSource tokenSource;
  private final String tokenHeader;

  public ServiceIdentityInterceptor(
      ProjectedServiceAccountTokenSource tokenSource, String tokenHeader) {
    this.tokenSource = tokenSource;
    this.tokenHeader = tokenHeader;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    if (request.header(tokenHeader) != null) {
      return chain.proceed(request);
    }
    return chain.proceed(
        tokenSource
            .get()
            .map(token -> request.newBuilder().header(tokenHeader, token).build())
            .orElse(request));
  }
}
