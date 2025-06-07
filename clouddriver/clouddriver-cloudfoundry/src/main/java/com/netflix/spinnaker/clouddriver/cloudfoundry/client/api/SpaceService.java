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
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstance;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Pagination;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Space;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface SpaceService {
  @GET("v3/spaces")
  Call<Pagination<Space>> all(
      @Query("page") Integer page,
      @Query("names") String names,
      @Query("organization_guids") String orgGuids);

  @GET("v3/spaces/{guid}")
  Call<Space> findById(@Path("guid") String guid);

  @GET("v2/spaces/{guid}/service_instances")
  Call<Page<ServiceInstance>> getServiceInstancesById(
      @Path("guid") String guid, @Query("page") Integer page, @Query("q") List<String> queryParams);
}
