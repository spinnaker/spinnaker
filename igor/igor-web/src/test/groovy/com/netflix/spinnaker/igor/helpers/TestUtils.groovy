/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.igor.helpers

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.igor.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.okhttp.OkHttpClientConfigurationProperties
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

class TestUtils {
  static ObjectMapper createObjectMapper() {
    ObjectMapper mapper = new ObjectMapper()
    mapper.registerModule(new JavaTimeModule())
    mapper
  }

  static SpinnakerHttpException makeSpinnakerHttpException(String url, int code, ResponseBody body){
    Response retrofit2Response = Response.error(code, body)

    Retrofit retrofit =
      new Retrofit.Builder()
        .baseUrl(RetrofitUtils.getBaseUrl(url))
        .addConverterFactory(JacksonConverterFactory.create())
        .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
        .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }

  static OkHttp3ClientConfiguration makeOkHttpClientConfig(){
    new OkHttp3ClientConfiguration(new OkHttpClientConfigurationProperties(), null, HttpLoggingInterceptor.Level.BASIC, null, null, null)
  }
}
