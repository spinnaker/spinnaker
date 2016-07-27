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

package com.netflix.spinnaker.gate.services.internal

import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.POST
import retrofit.http.Path

interface FiatService {

  @POST("/roles/sync")
  Response sync()

  @POST("/roles/{userId}")
  Response loginUser(@Path("userId") String userId, @Body String _ /* retrofit requires this */)

  @DELETE("/roles/{userId}")
  Response logoutUser(@Path("userId") String userId)
}
