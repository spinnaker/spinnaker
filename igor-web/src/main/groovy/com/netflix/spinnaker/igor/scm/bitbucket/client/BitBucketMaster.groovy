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

package com.netflix.spinnaker.igor.scm.bitbucket.client

import com.netflix.spinnaker.igor.config.BitBucketProperties
import com.squareup.okhttp.Credentials
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import retrofit.Endpoints
import retrofit.RequestInterceptor
import retrofit.RestAdapter
import retrofit.client.OkClient
import retrofit.converter.SimpleXMLConverter
import javax.validation.Valid

/**
 * Wrapper class for a collection of BitBucket clients
 */
class BitBucketMaster {
  BitBucketClient bitBucketClient
  String baseUrl

  @Bean
  @ConditionalOnProperty('bitbucket.base-url')
  BitBucketMaster bitBucketMaster(@Valid BitBucketProperties bitBucketProperties) {
    log.info "bootstrapping ${bitBucketProperties.baseUrl}"
    new BitBucketMaster(
        bitBucketClient : bitBucketClient(bitBucketProperties.baseUrl, bitBucketProperties.username, bitBucketProperties.password),
        baseUrl: bitBucketProperties.baseUrl)
  }

  BitBucketClient bitBucketClient(String address, String username, String password) {
    new RestAdapter.Builder()
      .setEndpoint(Endpoints.newFixedEndpoint(address))
      .setRequestInterceptor(new BasicAuthRequestInterceptor(username, password))
      .setClient(new OkClient())
      .setConverter(new SimpleXMLConverter())
      .build()
      .create(BitBucketClient(address:address))
  }

  static class BasicAuthRequestInterceptor implements RequestInterceptor {

    private final String username
    private final String password

    BasicAuthRequestInterceptor(String username, String password) {
      this.username = username
      this.password = password
    }

    @Override
    void intercept(RequestInterceptor.RequestFacade request) {
      request.addHeader("Authorization", Credentials.basic(username, password))
    }
  }
}
