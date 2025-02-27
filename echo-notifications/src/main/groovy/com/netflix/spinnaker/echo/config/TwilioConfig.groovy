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

package com.netflix.spinnaker.echo.config

import com.netflix.spinnaker.config.OkHttp3ClientConfiguration
import com.netflix.spinnaker.echo.jackson.EchoObjectMapper
import com.netflix.spinnaker.echo.twilio.TwilioService
import com.netflix.spinnaker.echo.util.RetrofitUtils
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.apache.commons.codec.binary.Base64
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory

@Configuration
@ConditionalOnProperty('twilio.enabled')
@Slf4j
@CompileStatic
class TwilioConfig {

    @Bean
    TwilioService twilioService(
      @Value('${twilio.account}') String username,
      @Value('${twilio.token}') String password,
      @Value('${twilio.base-url:https://api.twilio.com/}') String twilioBaseUrl,
      OkHttp3ClientConfiguration okHttpClientConfig) {

        log.info('twilio service loaded')

        String auth = "Basic " + Base64.encodeBase64String("${username}:${password}".getBytes())
        BasicAuthRequestInterceptor interceptor = new BasicAuthRequestInterceptor(auth);

        new Retrofit.Builder()
                .baseUrl(RetrofitUtils.getBaseUrl(twilioBaseUrl))
                .client(okHttpClientConfig.createForRetrofit2().addInterceptor(interceptor).build())
                .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
                .addConverterFactory(JacksonConverterFactory.create(EchoObjectMapper.getInstance()))
                .build()
                .create(TwilioService.class);
    }

  private static class BasicAuthRequestInterceptor implements Interceptor {

    private final String basic

    BasicAuthRequestInterceptor(String basic) {
      this.basic = basic
    }

    @Override
    Response intercept(Chain chain) throws IOException {
      Request request =
        chain
          .request()
          .newBuilder()
          .addHeader("Authorization", basic)
          .build()
      return chain.proceed(request)
    }
  }
}
