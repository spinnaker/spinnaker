/*
 * Copyright 2014 Netflix, Inc.
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


package com.netflix.spinnaker.orca.echo

import javax.xml.ws.Response
import retrofit.http.*

interface EchoService {

  @POST('/')
  Response recordEvent(@Body HashMap notification)

  @GET('/search/events/{time}')
  retrofit.client.Response getEvents(@Path("time") Long time, @Query("size") Long size, @Query("full") Boolean full,
                                     @Query("type") String type)
}
