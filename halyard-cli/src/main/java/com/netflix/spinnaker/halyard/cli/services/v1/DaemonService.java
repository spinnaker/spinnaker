/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.halyard.cli.services.v1;

import com.netflix.spinnaker.halyard.config.model.v1.node.Account;
import com.netflix.spinnaker.halyard.config.model.v1.node.DeploymentConfiguration;
import com.netflix.spinnaker.halyard.config.model.v1.node.Halconfig;
import com.netflix.spinnaker.halyard.config.model.v1.node.Provider;
import retrofit.http.*;

import java.util.List;

public interface DaemonService {
  @GET("/v1/config/")
  Halconfig getHalconfig();

  @GET("/v1/config/currentDeployment/")
  String getCurrentDeployment();

  @GET("/v1/config/deployments/")
  List<DeploymentConfiguration> getDeployments();

  @GET("/v1/config/deployments/{deployment}/")
  DeploymentConfiguration getDeployment(
      @Path("deployment") String deployment,
      @Query("validate") boolean validate);

  @GET("/v1/config/deployments/{deployment}/providers/{provider}/")
  Object getProvider(
      @Path("deployment") String deployment,
      @Path("provider") String provider,
      @Query("validate") boolean validate);

  @PUT("/v1/config/deployments/{deployment}/providers/{provider}/enabled/")
  Object setProviderEnabled(
      @Path("deployment") String deployment,
      @Path("provider") String provider,
      @Query("validate") boolean validate,
      @Body boolean enabled);

  @GET("/v1/config/deployments/{deployment}/providers/{provider}/accounts/{account}/")
  Object getAccount(
      @Path("deployment") String deployment,
      @Path("provider") String provider,
      @Path("account") String account,
      @Query("validate") boolean validate);
}
