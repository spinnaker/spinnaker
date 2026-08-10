/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.api;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.CreateRoute;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Route;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface RouteService {
  @GET("v3/routes")
  Call<Pagination<Route>> all(
      @Query("page") Integer page,
      @Query("per_page") Integer perPage,
      @Query("hosts") String hosts,
      @Query("organization_guids") String organizationGuids,
      @Query("domain_guids") String domainGuids,
      @Query("paths") String paths,
      @Query("ports") String ports,
      @Query("space_guids") String spaceGuids);

  @GET("v3/routes/{guid}")
  Call<Route> findById(@Path("guid") String guid);

  @POST("v3/routes")
  Call<Route> createRoute(@Body CreateRoute route);

  @DELETE("v3/routes/{guid}")
  Call<ResponseBody> deleteRoute(@Path("guid") String guid);
}
