/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.echo.pagerduty

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface PagerDutyService {
  @POST('/generic/2010-04-15/create_event.json')
  Call<Map> createEvent(@Header("Authorization") String authorization, @Body PagerDutyCreateEvent pagerDutyCreateEvent)

  static class PagerDutyCreateEvent {
    String event_type = "trigger"
    String client
    String service_key
    String description
    Map details
  }
}
