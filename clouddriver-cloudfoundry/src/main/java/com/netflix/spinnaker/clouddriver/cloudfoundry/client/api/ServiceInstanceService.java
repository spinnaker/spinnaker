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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.CreateServiceBinding;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.Page;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.ServiceInstanceInfo;
import retrofit.client.Response;
import retrofit.http.*;

public interface ServiceInstanceService {
  @GET("/v2/spaces/{guid}/service_instances")
  Page<ServiceInstanceInfo> all(@Query("page") Integer page, @Path("guid") String spaceGuid, @Query("q") String queryParam);

  @POST("/v2/service_bindings?accepts_incomplete=true")
  Response createServiceBinding(@Body CreateServiceBinding body);
}
