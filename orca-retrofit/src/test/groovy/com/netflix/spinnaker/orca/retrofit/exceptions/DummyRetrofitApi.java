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

package com.netflix.spinnaker.orca.retrofit.exceptions;

import retrofit.client.Response;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.GET;
import retrofit.http.HEAD;
import retrofit.http.PATCH;
import retrofit.http.POST;
import retrofit.http.PUT;
import retrofit.http.Path;

interface DummyRetrofitApi {
  @GET("/whatever")
  Response get();

  @HEAD("/whatever")
  Response head();

  @POST("/whatever")
  Response post(@Body String data);

  @PUT("/whatever")
  Response put();

  @PATCH("/whatever")
  Response patch(@Body String data);

  @DELETE("/whatever")
  Response delete();

  @GET("/whatever/{stuff}")
  Response getWithArg(@Path("stuff") String stuff);
}
