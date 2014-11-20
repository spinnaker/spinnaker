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

package com.netflix.spinnaker.gate.services

import retrofit.http.*

interface Front50Service {
  @GET('/{account}/applications')
  List<Map> getAll(@Path("account") String account)

  @GET('/{account}/applications/name/{name}')
  Map getMetaData(@Path('account') String account, @Path('name') String name)

  @DELETE('/{account}/applications/name/{name}')
  Map delete(@Path('account') String account, @Path('name') String name)

  @POST('/{account}/applications/name/{name}')
  Map create(@Path('account') String account, @Path('name') String name, @Body Map<String, String> app)
}
