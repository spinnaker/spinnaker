/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.mine

import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.PUT
import retrofit.http.Path
import retrofit.http.Query

interface MineService {
  @POST('/registerCanary')
  Response registerCanary(@Body Canary canary)

  @GET('/canaries/{id}')
  Canary checkCanaryStatus(@Path('id') String id)

  @DELETE('/canaries/{id}/cancel')
  Canary cancelCanary(@Path('id') String id, @Query('reason') String reason)

  @PUT('/canaries/{id}/disable')
  Canary disableCanary(@Path('id') String id, @Query('reason') String reason)

  @PUT('/canaries/{id}/enable')
  Canary enableCanary(@Path('id') String id)

  @PUT('/canaries/{id}/disableAndScheduleForTermination')
  Canary disableCanaryAndScheduleForTermination(@Path('id') String id, @Query('reason') String reason)
}
