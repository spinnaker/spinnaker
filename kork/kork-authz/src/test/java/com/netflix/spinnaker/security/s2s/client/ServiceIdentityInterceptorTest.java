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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import okhttp3.Interceptor;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ServiceIdentityInterceptorTest {

  private static final String HEADER = "X-Service-Identity-Token";

  @TempDir Path tempDir;

  @Test
  @DisplayName("presents the projected token on outbound requests")
  void addsTokenHeader() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "projected-token");

    Request sent = intercept(sourceFor(token), request().build());

    assertThat(sent.header(HEADER)).isEqualTo("projected-token");
  }

  @Test
  @DisplayName("sends no header when there is no token, rather than an empty credential")
  void omitsHeaderWithoutToken() throws IOException {
    Request sent = intercept(ProjectedServiceAccountTokenSource.disabled(), request().build());

    assertThat(sent.header(HEADER)).isNull();
  }

  @Test
  @DisplayName("an explicitly set identity wins over the ambient pod identity")
  void doesNotOverwriteExistingHeader() throws IOException {
    Path token = tempDir.resolve("token");
    Files.writeString(token, "projected-token");

    Request sent = intercept(sourceFor(token), request().header(HEADER, "explicit").build());

    assertThat(sent.header(HEADER)).isEqualTo("explicit");
  }

  private ProjectedServiceAccountTokenSource sourceFor(Path token) {
    return new ProjectedServiceAccountTokenSource(token, Duration.ofMinutes(1));
  }

  private static Request.Builder request() {
    return new Request.Builder().url("http://spin-front50:8080/auth/issueExecutionToken");
  }

  /** Runs the interceptor over a stub chain and returns the request it actually forwarded. */
  private static Request intercept(ProjectedServiceAccountTokenSource source, Request request)
      throws IOException {
    Interceptor.Chain chain = mock(Interceptor.Chain.class);
    when(chain.request()).thenReturn(request);
    when(chain.proceed(any()))
        .thenAnswer(
            invocation ->
                new Response.Builder()
                    .request(invocation.getArgument(0))
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .build());

    new ServiceIdentityInterceptor(source, HEADER).intercept(chain);

    ArgumentCaptor<Request> forwarded = ArgumentCaptor.forClass(Request.class);
    org.mockito.Mockito.verify(chain).proceed(forwarded.capture());
    return forwarded.getValue();
  }
}
