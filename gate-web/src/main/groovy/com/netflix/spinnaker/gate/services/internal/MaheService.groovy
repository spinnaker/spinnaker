/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services.internal

import org.springframework.web.bind.annotation.RequestBody
import retrofit.http.*

interface MaheService {

  @GET('/properties/app/{appName}')
  Map getFastPropertiesByApplication(@Path("appName") String appName)

  @GET("/properties/all")
  Map getAll()

  @GET( "/properties/key/{key}")
  Map getByKey(@Path("key") String key)

  @GET("/properties/all-keys")
  List<String> getAllKeys()


  @POST("/properties/impact")
  Map getImpact(@Body Map scope)

  @GET("/properties/scopeQuery")
  Map queryScope(@Body Map scope)

  @POST("/properties/create")
  Map create(@Body Map fastProperty)

  @POST("/promotion")
  String promote(@Body Map fastProperty)

  @GET("/promotion/{promotionId}")
  Map promotionStatus(@Path("promotionId") String promotionId)

  @PUT("/promotion/{promotionId}")
  Map passPromotion(@Path("promotionId") String promotionId , @Body Boolean pass)

  @GET("/promotions")
  List promotions()

  @GET("/promotions/{appId}")
  List promotionsByApp(@Path("appId") String appId)

  @DELETE("/properties/delete")
  Map delete(@Query("propId") String propId, @Query("cmcTicket") String cmcTicket, @Query("env") String env)

}
