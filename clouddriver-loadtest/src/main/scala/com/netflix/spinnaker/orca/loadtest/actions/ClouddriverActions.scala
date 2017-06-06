/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.loadtest.actions

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.http.request.builder.HttpRequestBuilder

object ClouddriverActions {
  val fetchApplications: HttpRequestBuilder = http(s"Fetch Applications (unexpanded)")
    .get("/applications")
    .header("Content-Type", "application/json")
    .check(status is 200)

  val fetchApplicationsExpanded: HttpRequestBuilder = http(s"Fetch Applications (expanded)")
    .get("/applications?expand=true")
    .header("Content-Type", "application/json")
    .check(status is 200)

  val fetchServerGroupExpanded: HttpRequestBuilder = http(s"Fetch Server Group (expanded)")
      .get("/applications/${name}/serverGroups?expand=true")
      .header("Content-Type", "application/json")
      .check(status is 200)
}
