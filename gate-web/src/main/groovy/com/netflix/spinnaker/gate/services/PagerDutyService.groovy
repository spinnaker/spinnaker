/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.gate.services

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import retrofit.http.GET
import retrofit.http.Query

interface PagerDutyService {
  @GET('/services?include%5B%5D=integrations&total=true&limit=100') //default limit is 25 & 100 is the max https://v2.developer.pagerduty.com/docs/pagination
  PagerDutyServiceResult getServices(@Query("offset") int offset)

  @GET('/oncalls?limit=100') //default limit is 25 & 100 is the max https://v2.developer.pagerduty.com/docs/pagination
  PagerDutyOnCallResult getOnCalls(@Query("offset") int offset)

  @JsonIgnoreProperties(ignoreUnknown = true)
  class PagerDutyServiceResult {
    boolean more
    List<Map> services
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  class PagerDutyOnCallResult {
    boolean more
    List<Map> oncalls
  }
}
