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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.orca.clouddriver.model.Task;
import com.netflix.spinnaker.orca.clouddriver.model.TaskOwner;
import retrofit.http.GET;
import retrofit.http.Path;

public interface CloudDriverTaskStatusService {
  @GET("/task/{id}")
  Task lookupTask(@Path("id") String id);

  @GET("/{cloudProvider}/task/{id}/owner")
  TaskOwner lookupTaskOwner(@Path("cloudProvider") String cloudProvider, @Path("id") String id);
}
