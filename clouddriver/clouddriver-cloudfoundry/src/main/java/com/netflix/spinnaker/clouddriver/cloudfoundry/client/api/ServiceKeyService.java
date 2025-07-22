/*
 * Copyright 2019 Pivotal, Inc.
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

import com.netflix.spinnaker.clouddriver.cloudfoundry.client.model.v2.*;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ServiceKeyService {
  @POST("v2/service_keys")
  Call<Resource<ServiceCredentials>> createServiceKey(@Body CreateServiceKey body);

  @GET("v2/service_keys")
  Call<Page<ServiceKey>> getServiceKey(
      @Query("page") Integer page, @Query("q") List<String> queryParams);

  @DELETE("v2/service_keys/{guid}")
  Call<ResponseBody> deleteServiceKey(@Path("guid") String guid);
}
