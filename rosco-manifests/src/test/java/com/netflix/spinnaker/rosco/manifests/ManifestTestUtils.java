/*
 * Copyright 2021 Salesforce, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.rosco.manifests;

import com.netflix.spinnaker.kork.retrofit.exceptions.RetrofitException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import okhttp3.MediaType;
import okhttp3.ResponseBody;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

public class ManifestTestUtils {

  public static SpinnakerHttpException makeSpinnakerHttpException(int status) {
    // SpinnakerHttpException spinnakerHttpException = mock(SpinnakerHttpException.class);
    // when(spinnakerHttpException.getMessage()).thenReturn("message");
    // when(spinnakerHttpException.getResponseCode()).thenReturn(status);
    // return spinnakerHttpException;
    //
    // would be sufficient, except in the chained case, where the return value
    // of this method is the cause of a real SpinnakerHttpException object.
    // There, getResponseCode needs a real underlying response, at least real
    // enough for retrofit2Response.code() to work.  So, go ahead and build one.
    String url = "https://some-url";
    retrofit2.Response retrofit2Response =
        retrofit2.Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), "{ \"message\": \"arbitrary message\" }"));

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build();

    return new SpinnakerHttpException(RetrofitException.httpError(retrofit2Response, retrofit));
  }
}
