/*
 * Copyright 2017 Google, Inc.
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
package com.netflix.spinnaker.clouddriver.google

import com.google.api.client.googleapis.batch.BatchRequest
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpResponseException
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry

import java.util.concurrent.TimeUnit


trait GoogleExecutorTraits {
  final String TAG_BATCH_CONTEXT = "context"
  final String TAG_REGION = "region"
  final String TAG_SCOPE = "scope"
  final String TAG_ZONE = "zone"
  final String SCOPE_BATCH = "batch"
  final String SCOPE_GLOBAL = "global"
  final String SCOPE_REGIONAL = "regional"
  final String SCOPE_ZONAL = "zonal"

  abstract Registry getRegistry()


  public <T> T timeExecuteBatch(BatchRequest batch, String batchContext, String... tags) throws IOException {
     def batchSize = batch.size()
     def success = "false"
     Registry registry = getRegistry()
     Clock clock = registry.clock()
     long startTime = clock.monotonicTime()
     int statusCode = 200

     try {
       batch.execute()
       success = "true"
     } catch (HttpResponseException e) {
       statusCode = e.getStatusCode()
     } finally {
       def status = statusCode.toString()[0] + "xx"

       def tagDetails = [(TAG_BATCH_CONTEXT): batchContext, "success": success, "status": status, "statusCode": statusCode.toString()]
       long nanos = clock.monotonicTime() - startTime
       registry.timer(registry.createId("google.batchExecute", tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
       registry.counter(registry.createId("google.batchSize", tags).withTags(tagDetails)).increment(batchSize)
     }
  }

  public <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) throws IOException {
     def success = "false"
     T result
     Registry registry = getRegistry()
     Clock clock = registry.clock()
     long startTime = clock.monotonicTime()
     int statusCode = -1

     try {
       result = request.execute()
       success = "true"
       statusCode = request.getLastStatusCode()
     } catch (HttpResponseException e) {
       statusCode = e.getStatusCode()
       throw e
     } finally {
       long nanos = clock.monotonicTime() - startTime
       def status = statusCode.toString()[0] + "xx"

       def tagDetails = ["api": api, "success": success, "status": status, "statusCode": statusCode.toString() ]
       registry.timer(registry.createId("google.api", tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
     }
     return result
  }
}

