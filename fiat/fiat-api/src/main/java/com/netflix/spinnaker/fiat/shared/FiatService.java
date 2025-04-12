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
import java.util.Collection;
import java.util.List;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

public interface FiatService {

  /**
   * @param userId The username of the user
   * @return The full UserPermission of the user.
   */
  @GET("authorize/{userId}")
  Call<UserPermission.View> getUserPermission(@Path("userId") String userId);

  /**
   * @param userId The username of the user
   * @param resourceType The resource type in question (application, account, etc.)
   * @param resourceName The name of the resource
   * @param authorization The authorization in question (read, write, etc)
   * @return True if the user has access to the specified resource.
   */
  @GET("authorize/{userId}/{resourceType}/{resourceName}/{authorization}")
  Call<Void> hasAuthorization(
      @Path("userId") String userId,
      @Path("resourceType") String resourceType,
      @Path("resourceName") String resourceName,
      @Path("authorization") String authorization);

  /**
   * Determine whether the user can create a resource. Returns 200 if the user can, throws 404
   * otherwise
   *
   * @param userId The username of the user
   * @param resourceType The type of the resource
   * @param resource The resource to check
   */
  @POST("authorize/{userId}/{resourceType}/create")
  Call<Void> canCreate(
      @Path("userId") String userId,
      @Path("resourceType") String resourceType,
      @Body Object resource);

  /**
   * Use to update all users.
   *
   * @return The number of non-anonymous users synced.
   */
  @POST("roles/sync")
  Call<Long> sync();

  /**
   * Use to update a subset of users. An empty list will update the anonymous/unrestricted user.
   *
   * @param roles Users with any role listed should be updated.
   * @return The number of non-anonymous users synced.
   */
  @POST("roles/sync")
  Call<Long> sync(@Body List<String> roles);

  /**
   * Use to update a service account. As opposed to `sync`, this will not trigger a full sync for
   * user role membership.
   *
   * @param serviceAccountId Name of the service account.
   * @param roles The roles allowed for this service account.
   * @return The number of non-anonymous users synced.
   */
  @POST("roles/sync/serviceAccount/{serviceAccountId}")
  Call<Long> syncServiceAccount(
      @Path("serviceAccountId") String serviceAccountId, @Body List<String> roles);

  /**
   * @param userId The user being logged in
   * @return ignored.
   */
  @POST("roles/{userId}")
  Call<Void> loginUser(@Path("userId") String userId);

  /**
   * Used specifically for logins that contain the users roles/groups.
   *
   * @param userId The user being logged in
   * @param roles Collection of roles from the identity provider
   * @return ignored.
   */
  @PUT("roles/{userId}")
  Call<Void> loginWithRoles(@Path("userId") String userId, @Body Collection<String> roles);

  /**
   * @param userId The user being logged out
   * @return ignored.
   */
  @DELETE("roles/{userId}")
  Call<Void> logoutUser(@Path("userId") String userId);
}
