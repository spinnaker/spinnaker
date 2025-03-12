/*
 * Copyright 2017 Google, Inc.
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
 *
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul;

import com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service.consul.model.ConsulServiceNodeDetails;
import java.util.List;
import retrofit.http.GET;
import retrofit.http.Path;
import retrofit.http.Query;

public interface ConsulApi {
  @GET("/v1/health/service/{serviceName}")
  List<ConsulServiceNodeDetails> serviceHealth(
      @Path("serviceName") String serviceName, @Query("passing") boolean passing);
}
