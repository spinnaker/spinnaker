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

package com.netflix.spinnaker.gate.services.internal

import retrofit.http.GET
import retrofit.http.Path
import retrofit.http.Query

interface MortService {

  @GET('/credentials')
  List<String> getAccountNames()

  @GET('/credentials/{account}')
  Map getAccount(@Path("account") String account)

  @GET('/securityGroups')
  Map getSecurityGroups()

  @GET('/search')
  List<Map> search(@Query("q") String query,
                   @Query("type") String type,
                   @Query("platform") String platform,
                   @Query("pageSize") Integer size,
                   @Query("page") Integer offset)

  @GET('/securityGroups/{account}/{type}')
  Map getSecurityGroups(@Path("account") String account, @Path("type") String type, @Query("region") String region)

  @GET('/securityGroups/{account}/{type}/{region}/{name}')
  Map getSecurityGroup(@Path("account") String account, @Path("type") String type, @Path("name") String name,
                       @Path("region") String region, @Query("vpcId") String vpcId)

  @GET('/instanceTypes')
  List<Map> getInstanceTypes()

  @GET('/keyPairs')
  List<Map> getKeyPairs()

  @GET('/subnets')
  List<Map> getSubnets()

  @GET('/vpcs')
  List<Map> getVpcs()
}
