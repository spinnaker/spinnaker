/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.orca

import com.netflix.spinnaker.orca.keel.model.DeliveryConfig
import retrofit.client.Response
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.Header
import retrofit.http.Headers
import retrofit.http.POST
import retrofit.http.Path

interface KeelService {
  @POST("/delivery-configs/")
  @Headers("Accept: application/json")
  fun publishDeliveryConfig(@Body deliveryConfig: DeliveryConfig, @Header(value = "X-SPINNAKER-USER") user: String): Response

  @DELETE("/application/{application}/config")
  fun deleteDeliveryConfig(@Path("application") application: String): Response
}
