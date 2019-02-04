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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Resource;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Route;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.RouteMapping;
import retrofit.client.Response;
import retrofit.http.*;

import java.util.List;

public interface RouteService {
  //Mapping to CF API style query params - https://apidocs.cloudfoundry.org/1.34.0/routes/list_all_routes.html
  @GET("/v2/routes?results-per-page=100")
  Page<Route> all(@Query("page") Integer page, @Query("q") List<String> queryParams);

  @GET("/v2/routes/{guid}")
  Resource<Route> findById(@Path("guid") String guid);

  @GET("/v2/routes/{guid}/route_mappings")
  Page<RouteMapping> routeMappings(@Path("guid") String guid, @Query("page") Integer page);

  @POST("/v2/routes")
  Resource<Route> createRoute(@Body Route route);

  @DELETE("/v2/routes/{guid}?recursive=true")
  Response deleteRoute(@Path("guid") String guid);
}
