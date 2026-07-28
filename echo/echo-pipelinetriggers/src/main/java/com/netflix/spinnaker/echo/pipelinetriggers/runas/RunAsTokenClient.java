/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.echo.pipelinetriggers.runas;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

/**
 * Retrofit client for Front50's dedicated run-as token mint/exchange endpoint (Component 7).
 *
 * <p>Echo trigger paths use {@code AuthenticatedRequest.runAs(serviceAccount, ...)} but do not
 * resolve the service account's roles themselves and do not hold a signing key. Instead they call
 * this endpoint to exchange a managed service-account name for a short-lived, cryptographically
 * signed identity token (Front50 owns managed service accounts and is the only minter besides
 * Gate). Authorization is Echo's service-to-service caller identity (Front50 requires the
 * authenticated caller to be Echo via {@code authz.s2s}), so no signing key or signed assertion is
 * involved. The token is then propagated downstream in {@link
 * com.netflix.spinnaker.kork.common.Header#IDENTITY_TOKEN} so Orca / Front50 act only on roles
 * Front50 vouched for. Mirrors the existing Echo {@code Front50Service} Retrofit client.
 */
public interface RunAsTokenClient {

  @POST("auth/runAsToken")
  @Headers("Accept: application/json")
  Call<RunAsTokenResponse> mintRunAsToken(@Body RunAsTokenRequest request);
}
