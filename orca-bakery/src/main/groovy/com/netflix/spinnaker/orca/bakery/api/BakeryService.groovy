/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.bakery.api

import retrofit.http.Body
import retrofit.http.GET
import retrofit.http.POST
import retrofit.http.Path
import rx.Observable

/**
 * An interface to the Bakery's REST API. See {@link https://confluence.netflix.com/display/ENGTOOLS/Bakery+API}.
 */
interface BakeryService {

  @POST("/api/v1/{region}/bake")
  Observable<BakeStatus> createBake(@Path("region") String region, @Body Bake bake)

  @POST("/api/v1/{region}/bake")
  Observable<BakeStatus> createBake(@Path("region") String region)

  @GET("/api/v1/{region}/status/{id}")
  Observable<BakeStatus> lookupStatus(@Path("region") String region, @Path("id") String id)

}