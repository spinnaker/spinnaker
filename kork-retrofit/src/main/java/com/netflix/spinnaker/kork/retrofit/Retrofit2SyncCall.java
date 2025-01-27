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

package com.netflix.spinnaker.kork.retrofit;

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import java.io.IOException;
import retrofit2.Call;
import retrofit2.Response;

public class Retrofit2SyncCall<T> {

  /**
   * Handle IOExceptions from {@link Call}.execute method centrally, instead of all places that make
   * retrofit2 API calls.
   *
   * @throws SpinnakerNetworkException if IOException occurs.
   * @param <T> Successful response body type.
   */
  public static <T> T execute(Call<T> call) {
    return executeCall(call).body();
  }

  /**
   * Handle IOExceptions from {@link Call}.execute method centrally, instead of all places that make
   * retrofit2 API calls.
   *
   * @throws SpinnakerNetworkException if IOException occurs.
   * @param call call to be executed
   * @return Response<T> response after execution
   */
  public static <T> Response<T> executeCall(Call<T> call) {
    try {
      return call.execute();
    } catch (IOException e) {
      throw new SpinnakerNetworkException(e, call.request());
    }
  }
}
