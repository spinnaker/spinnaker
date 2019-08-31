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

package com.netflix.spinnaker.orca.bakery.api

import com.netflix.spinnaker.kork.artifacts.model.Artifact
import com.netflix.spinnaker.orca.bakery.api.manifests.BakeManifestRequest
import retrofit.http.*
import rx.Observable


/**
 * An interface to the Bakery's REST API.
 */
interface BakeryService {

  @POST("/api/v2/manifest/bake/{type}")
  Artifact bakeManifest(@Path("type") String type, @Body BakeManifestRequest bake)

  @POST("/api/v1/{region}/bake")
  Observable<BakeStatus> createBake(@Path("region") String region, @Body BakeRequest bake, @Query("rebake") String rebake)

  @GET("/api/v1/{region}/status/{statusId}")
  Observable<BakeStatus> lookupStatus(@Path("region") String region, @Path("statusId") String statusId)

  @GET("/api/v1/{region}/bake/{bakeId}")
  Observable<Bake> lookupBake(@Path("region") String region, @Path("bakeId") String bakeId)

  //
  // Methods below this line are not supported by the Netflix Bakery, and are only available
  // iff bakery.roscoApisEnabled is true.
  //

  @GET("/bakeOptions/{cloudProvider}/baseImages/{imageId}")
  Observable<BaseImage> getBaseImage(@Path("cloudProvider") String cloudProvider,
                                     @Path("imageId") String imageId)
}
