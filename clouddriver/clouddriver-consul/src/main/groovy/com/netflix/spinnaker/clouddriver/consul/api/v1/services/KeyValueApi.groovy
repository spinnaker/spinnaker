/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.consul.api.v1.services

import com.netflix.spinnaker.clouddriver.consul.api.v1.model.KeyValuePair
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface KeyValueApi {
  @GET("/v1/kv/{key}")
  Call<List<KeyValuePair>> getKey(@Path("key") String key, @Query("dc") String dc, @Query("recurse") Boolean recurse)

  @PUT("/v1/kv/{key}")
  Call<ResponseBody> putKey(@Path("key") String key, @Body String data, @Query("dc") String dc)

  @DELETE("/v1/kv/{key}")
  Call<ResponseBody> deleteKey(@Path("key") String key, @Query("dc") String dc, @Query("recurse") Boolean recurse)
}
