/*
 * Copyright 2021 Armory, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.clouddriver.cloudfoundry.client.api;

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.Process;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ProcessResources;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.ScaleProcess;
import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v3.UpdateProcess;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ProcessesService {

  @POST("/v3/processes/{guid}/actions/scale")
  Call<ResponseBody> scaleProcess(@Path("guid") String guid, @Body ScaleProcess scaleProcess);

  @PATCH("/v3/processes/{guid}")
  Call<Process> updateProcess(@Path("guid") String guid, @Body UpdateProcess updateProcess);

  @GET("/v3/processes/{guid}")
  Call<Process> findProcessById(@Path("guid") String guid);

  @GET("/v3/processes/{guid}/stats")
  Call<ProcessResources> findProcessStatsById(@Path("guid") String guid);
}
