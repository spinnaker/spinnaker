/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.flex

import com.netflix.spinnaker.orca.flex.model.ElasticIpRequest
import com.netflix.spinnaker.orca.flex.model.ElasticIpResult
import retrofit.http.Body
import retrofit.http.DELETE
import retrofit.http.POST
import retrofit.http.Path

interface FlexService {
  @POST("/applications/{application}/clusters/{account}/{cluster}/elasticIps/{region}")
  ElasticIpResult associateElasticIp(@Path("application") String application,
                                     @Path("account") String account,
                                     @Path("cluster") String cluster,
                                     @Path("region") String region,
                                     @Body ElasticIpRequest request)

  @DELETE("/applications/{application}/clusters/{account}/{cluster}/elasticIps/{region}/{address}")
  ElasticIpResult disassociateElasticIp(@Path("application") String application,
                                        @Path("account") String account,
                                        @Path("cluster") String cluster,
                                        @Path("region") String region,
                                        @Path("address") String address)
}

