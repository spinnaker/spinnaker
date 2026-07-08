/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
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

package com.netflix.spinnaker.igor.config;

import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketClient;
import com.netflix.spinnaker.igor.scm.bitbucket.client.BitBucketMaster;
import com.netflix.spinnaker.kork.retrofit.ErrorHandlingExecutorCallAdapterFactory;
import com.netflix.spinnaker.kork.retrofit.util.RetrofitUtils;
import com.netflix.spinnaker.config.OkHttp3ClientConfiguration;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Credentials;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import jakarta.validation.Valid;

import java.io.IOException;

/**
 * Converts the list of BitBucket Configuration properties a collection of clients to access the BitBucket hosts
 */
@Configuration
@ConditionalOnProperty("bitbucket.base-url")
@Slf4j
@EnableConfigurationProperties(BitBucketProperties.class)
public class BitBucketConfig {

  @Bean
  public BitBucketMaster bitBucketMaster(@Valid BitBucketProperties bitBucketProperties, OkHttp3ClientConfiguration okHttpClientConfig) {
    log.info("bootstrapping {} as bitbucket", bitBucketProperties.getBaseUrl());
    BitBucketMaster master = new BitBucketMaster();
    master.setBitBucketClient(bitBucketClient(bitBucketProperties.getBaseUrl(), bitBucketProperties.getUsername(), bitBucketProperties.getPassword(), okHttpClientConfig));
    master.setBaseUrl(bitBucketProperties.getBaseUrl());
    return master;
  }

  public BitBucketClient bitBucketClient(String address, String username, String password, OkHttp3ClientConfiguration okHttpClientConfig) {
    return new Retrofit.Builder()
      .baseUrl(RetrofitUtils.getBaseUrl(address))
      .client(okHttpClientConfig.createForRetrofit2().addInterceptor(new BasicAuthRequestInterceptor(username, password)).build())
      .addConverterFactory(JacksonConverterFactory.create())
      .addCallAdapterFactory(ErrorHandlingExecutorCallAdapterFactory.getInstance())
      .build()
      .create(BitBucketClient.class);
  }

  public static class BasicAuthRequestInterceptor implements Interceptor {

    private final String username;
    private final String password;

    public BasicAuthRequestInterceptor(String username, String password) {
      this.username = username;
      this.password = password;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
      Request request = chain.request().newBuilder().addHeader("Authorization", Credentials.basic(username, password)).build();
      return chain.proceed(request);
    }
  }

}
