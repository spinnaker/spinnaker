/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.core.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import retrofit.http.*

interface Front50Service {
  @GET("/credentials")
  List<Map> getCredentials()

  @GET('/{account}/applications/search')
  List<Map> searchByName(@Path('account') String account, @Query("name") String name)

  @GET('/v2/projects/search')
  HalList searchForProjects(@Query("q") String query)

  @JsonIgnoreProperties(ignoreUnknown = true)
  static class HalList {
    @JsonProperty("_embedded")
    Map<String, List<Map>> embedded
  }
}
