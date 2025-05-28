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

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.HEAD;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;

interface DummyRetrofitApi {
  @GET("/whatever")
  Call<ResponseBody> get();

  @HEAD("/whatever")
  Call<Void> head();

  @POST("/whatever")
  Call<ResponseBody> post(@Body String data);

  @PUT("/whatever")
  Call<ResponseBody> put();

  @PATCH("/whatever")
  Call<ResponseBody> patch(@Body String data);

  @DELETE("/whatever")
  Call<ResponseBody> delete();

  @GET("/whatever/{stuff}")
  Call<ResponseBody> getWithArg(@Path("stuff") String stuff);
}
