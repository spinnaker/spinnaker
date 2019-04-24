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
import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

import java.util.Collection;
import java.util.List;

public interface FiatService {

  /**
   * @param userId The username of the user
   * @return The full UserPermission of the user.
   */
  @GET("/authorize/{userId}")
  UserPermission.View getUserPermission(@Path("userId") String userId);

  /**
   * @param userId The username of the user
   * @param resourceType The resource type in question (application, account, etc.)
   * @param resourceName The name of the resource
   * @param authorization The authorization in question (read, write, etc)
   * @return True if the user has access to the specified resource.
   */
  @GET("/authorize/{userId}/{resourceType}/{resourceName}/{authorization}")
  Response hasAuthorization(@Path("userId") String userId,
                            @Path("resourceType") String resourceType,
                            @Path("resourceName") String resourceName,
                            @Path("authorization") String authorization);

  /**
   * Use to update all users.
   * @return The number of non-anonymous users synced.
   */
  @POST("/roles/sync")
  long sync();

  /**
   * Use to update a subset of users. An empty list will update the anonymous/unrestricted user.
   *
   * @param roles Users with any role listed should be updated.
   * @return The number of non-anonymous users synced.
   */
  @POST("/roles/sync")
  long sync(@Body List<String> roles);

  /**
   * @param userId The user being logged in
   * @param ignored ignored.
   * @return ignored.
   */
  @POST("/roles/{userId}")
  Response loginUser(@Path("userId") String userId, @Body String ignored /* retrofit requires this */);


  /**
   * Used specifically for logins that contain the users roles/groups.
   * @param userId The user being logged in
   * @param roles Collection of roles from the identity provider
   * @return ignored.
   */
  @PUT("/roles/{userId}")
  Response loginWithRoles(@Path("userId") String userId, @Body Collection<String> roles);

  /**
   * @param userId The user being logged out
   * @return ignored.
   */
  @DELETE("/roles/{userId}")
  Response logoutUser(@Path("userId") String userId);
}
