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
import com.squareup.okhttp.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.PUT
import retrofit.http.Path
import retrofit.http.Query

interface KeyValueApi {
  @GET("/v1/kv/{key}")
  List<KeyValuePair> getKey(@Path("key") String key, @Query("dc") String dc, @Query("recurse") Boolean recurse)

  @PUT("/v1/kv/{key}")
  Response putKey(@Body String data, @Query("dc") String dc)

  @DELETE("/v1/kv/{key}")
  Response deleteKey(@Path("key") String key, @Query("dc") String dc, @Query("recurse") Boolean recurse)
}
