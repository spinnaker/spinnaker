/*
 * Copyright 2017 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.spinnaker.fiat.model.resources.Application;
import com.netflix.spinnaker.fiat.model.resources.ServiceAccount;
import java.util.List;
import retrofit.http.GET;
import retrofit.http.Query;

public interface Front50Api {
  @GET("/permissions/applications")
  List<Application> getAllApplicationPermissions();

  /**
   * @deprecated for fiat's usage this is always going to be called with restricted = false, use the
   *     no arg method instead which has the same behavior.
   */
  @GET("/v2/applications")
  @Deprecated
  List<Application> getAllApplications(@Query("restricted") boolean restricted);

  @GET("/v2/applications?restricted=false")
  List<Application> getAllApplications();

  @GET("/serviceAccounts")
  List<ServiceAccount> getAllServiceAccounts();
}
