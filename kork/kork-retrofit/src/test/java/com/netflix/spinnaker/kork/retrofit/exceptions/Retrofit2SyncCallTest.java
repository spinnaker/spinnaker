/*
 * Copyright 2023 OpsMx, Inc.
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

package com.netflix.spinnaker.kork.retrofit.exceptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.kork.retrofit.Retrofit2SyncCall;
import java.io.IOException;
import okhttp3.HttpUrl;
import okhttp3.Request;
import org.junit.jupiter.api.Test;
import retrofit2.Call;
import retrofit2.Response;

class Retrofit2SyncCallTest {

  @Test
  void testExecuteSuccess() throws IOException {
    Call<String> mockCall = mock(Call.class);
    String responseBody = "testing";
    when(mockCall.execute()).thenReturn(Response.success(responseBody));
    String execute = Retrofit2SyncCall.execute(mockCall);
    assertThat(execute).isEqualTo(responseBody);
  }

  @Test
  void testExecuteThrowException() throws IOException {
    Call<String> mockCall = mock(Call.class);
    IOException ioException = new IOException("exception test");
    when(mockCall.execute()).thenThrow(ioException);

    HttpUrl url = HttpUrl.parse("http://arbitrary-url");
    Request mockRequest = mock(Request.class);
    when(mockCall.request()).thenReturn(mockRequest);
    when(mockRequest.url()).thenReturn(url);

    SpinnakerNetworkException thrown =
        catchThrowableOfType(
            () -> Retrofit2SyncCall.execute(mockCall), SpinnakerNetworkException.class);
    assertThat(thrown).hasCause(ioException);
    assertThat(thrown.getUrl()).isEqualTo(url.toString());
  }
}
