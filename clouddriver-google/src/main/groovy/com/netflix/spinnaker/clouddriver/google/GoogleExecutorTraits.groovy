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
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpResponseException
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Id
import com.netflix.spectator.api.Registry

import java.util.concurrent.TimeUnit;


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
     def success = "false"
     Registry registry = getRegistry()
     Clock clock = registry.clock()
     long startTime = clock.monotonicTime()

     try {
       batch.execute()
       success = "true"
     } finally {
       def tagDetails = [(TAG_BATCH_CONTEXT): batchContext, "success": success]
       long nanos = clock.monotonicTime() - startTime
       registry.timer(registry.createId("google.batchExecute", tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
       registry.counter(registry.createId("google.batchSize", tags).withTags(tagDetails)).increment(batch.size())
     }
  }

  public <T> T timeExecute(AbstractGoogleClientRequest<T> request, String api, String... tags) throws IOException {
     def success = "false"
     T result
     Registry registry = getRegistry()
     Clock clock = registry.clock()
     long startTime = clock.monotonicTime()

     try {
       result = request.execute()
       success = "true"
     } finally {
       long nanos = clock.monotonicTime() - startTime
       def tagDetails = ["api": api, "success": success, statusCode: request.getLastStatusCode().toString()]
       registry.timer(registry.createId("google.api", tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
     }
     return result
  }
}
  
