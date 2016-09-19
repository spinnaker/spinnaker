/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.shared;

import com.netflix.spinnaker.fiat.model.UserPermission;
import com.squareup.okhttp.Response;
import retrofit.http.Body;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;

import java.util.List;

public interface FiatService {

  @GET("/authorize/{userId}")
  UserPermission.View getUserPermission(@Path("userId") String userId);

  @GET("/authorize/{userId}/{resourceType}/{resourceName}/{authorization}")
  Response hasAuthorization(@Path("userId") String userId,
                            @Path("resourceType") String resourceType,
                            @Path("resourceName") String resourceName,
                            @Path("authorization") String authorization);

  /**
   * Use to update a subset of users. An empty list will update the anonymous/unrestricted user.
   *
   * @return The number of non-anonymous users synced.
   */
  @POST("/roles/sync")
  long sync(@Body List<String> roles);
}
