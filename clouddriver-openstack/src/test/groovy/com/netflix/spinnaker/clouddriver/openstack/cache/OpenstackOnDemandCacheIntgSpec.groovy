/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.cache

import com.squareup.okhttp.MediaType
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.RequestBody
import com.squareup.okhttp.Response
import com.sun.xml.internal.ws.util.CompletedFuture
import org.springframework.http.HttpStatus
import spock.lang.Ignore
import spock.lang.Specification

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.stream.Collectors
import java.util.stream.IntStream

class OpenstackOnDemandCacheIntgSpec extends Specification {
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8")
  OkHttpClient client

  void 'setup'() {
    client = new OkHttpClient()
  }

  // Use for local testing
  @Ignore
  void 'parallel on-demand call' () {
    given:
    ExecutorService executor = Executors.newFixedThreadPool(20)

    and:
    String requestBody = '{"serverGroupName": "myapp-teststack-v002", "account": "test", "region": "east"}'
    RequestBody body = RequestBody.create(JSON, requestBody)
    Request request = new Request.Builder()
      .url('http://localhost:7002/cache/openstack/serverGroup')
      .post(body)
      .build()

    when:
    List<CompletedFuture<Response>> completedFutureList = IntStream.rangeClosed(0, 20)
      .boxed()
      .map { index -> CompletableFuture.supplyAsync ({ client.newCall(request).execute() }, executor) }
      .collect(Collectors.toList())

    then:
    completedFutureList.every {
      it.get().code() == HttpStatus.ACCEPTED.value()
    }
  }
}
