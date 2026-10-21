/*
 * Copyright 2026 Spinnaker contributors.
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.clouddriver.model.EcsServiceDeploymentStatus;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;

/**
 * Client for ecs-native service-deployment status. The expected service-deployment ARN pins the
 * read to the service deployment created by the preceding write operation.
 */
public interface EcsNativeService {

  @GET("/ecs-native/serverGroups/{account}/{region}/{serverGroupName}/deploymentStatus")
  Call<EcsServiceDeploymentStatus> getServiceDeploymentStatus(
      @Path("account") String account,
      @Path("region") String region,
      @Path("serverGroupName") String serverGroupName,
      @Query("expectedServiceDeploymentArn") String expectedServiceDeploymentArn);
}
