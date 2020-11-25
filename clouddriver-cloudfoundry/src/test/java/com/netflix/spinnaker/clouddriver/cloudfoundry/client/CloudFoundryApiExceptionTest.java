/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.ErrorDescription;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import retrofit.RetrofitError;
import retrofit.client.Response;

class CloudFoundryApiExceptionTest {
  @Test
  void constructorWithErrorDescription() {
    Response response = new Response("url", 500, "reason", Collections.emptyList(), null);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getResponse()).thenReturn(response);
    CloudFoundryApiException e =
        new CloudFoundryApiException(
            new ErrorDescription()
                .setErrors(
                    Arrays.asList(
                        new ErrorDescription().setDetail("Main Error"),
                        new ErrorDescription().setDetail("Foo"))),
            retrofitError);

    assertThat(e.getMessage()).contains("Main Error and Foo");
  }

  @Test
  void constructorHandlesNullErrorDescription() {
    Response response = new Response("url", 500, "reason", Collections.emptyList(), null);
    RetrofitError retrofitError = mock(RetrofitError.class);
    when(retrofitError.getResponse()).thenReturn(response);
    CloudFoundryApiException e =
        new CloudFoundryApiException((ErrorDescription) null, retrofitError);

    assertThat(e.getMessage()).contains("status: 500. url: url. raw response body: null");
  }
}
