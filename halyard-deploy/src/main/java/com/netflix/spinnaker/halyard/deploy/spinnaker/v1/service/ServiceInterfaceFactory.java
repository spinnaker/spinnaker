/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.deploy.spinnaker.v1.service;

import com.jakewharton.retrofit.Ok3Client;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RestAdapter;

@Component
public class ServiceInterfaceFactory {
  @Autowired Ok3Client ok3Client;

  @Autowired RestAdapter.LogLevel retrofitLogLevel;

  public <T> T createService(String endpoint, SpinnakerService<T> service) {
    Class<T> clazz = service.getEndpointClass();
    return new RestAdapter.Builder()
        .setClient(ok3Client)
        .setLogLevel(retrofitLogLevel)
        .setEndpoint(endpoint)
        .build()
        .create(clazz);
  }
}
