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

import com.google.api.client.googleapis.services.AbstractGoogleClientRequest
import com.google.api.client.http.HttpResponseException
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.google.batch.GoogleBatchRequest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.PostConstruct
import java.util.concurrent.TimeUnit

/**
 * Provides a static-ish means to wrap API execution calls with spectator metrics.
 *
 * Spring makes this ugly.
 *
 * Normally there could be a "traits" providing this behavior with the class.
 * However traits are for instances not classes. Static class methods making API calls
 * are not instances. Thus we need to access this statically.
 * The registry throws a wrench into this because it is typically autowired in, however
 * autowiring is for instances not classes and static methods are on classes not instances.
 *
 * So this class provides a static registry that those static methods can use (traditionally
 * the calling class autowires its registry, but that isnt available). In order to implement
 * that static registry we "secretly" autowire it into a class instance then use the
 * PostConstruct method to bind the class static to the autowired registry.
 *
 * Did I mention Spring makes this ugly? It gets worse.
 *
 * The spring autowiring and initialization is random. So if that static method that this
 * is supporting wants to make a call during its initialization (e.g. calls made during
 * the GoogleCredentials initialization in clouddriver-google) then this module wont
 * necessarily be initialized yet. So the calling module needs to ensure this module is
 * first initialized by autowiring a GoogleExecutor even though it doesnt really need or
 * use the executor instance itself. Hence the @Component annotation.
 *
 * Ugly, but the Spring patterns and style are firmly entrenched within Spinnaker
 * so "when in Rome..."
 */
@Component
class GoogleExecutor {
  static Registry globalRegistry

  static Registry getRegistry() {
    return globalRegistry
  }

  @Autowired
  Registry autowiredRegistry

  @PostConstruct
  public void bindGlobalRegistry() {
    globalRegistry = autowiredRegistry
  }

  final static String TAG_BATCH_CONTEXT = "context"
  final static String TAG_REGION = "region"
  final static String TAG_SCOPE = "scope"
  final static String TAG_ZONE = "zone"
  final static String SCOPE_GLOBAL = "global"
  final static String SCOPE_REGIONAL = "regional"
  final static String SCOPE_ZONAL = "zonal"

  public static <T> T timeExecuteBatch(Registry spectator_registry, GoogleBatchRequest batch, String batchContext, String... tags) throws IOException {
     def batchSize = batch.size()
     def success = "false"
     Clock clock = spectator_registry.clock()
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
       spectator_registry.timer(spectator_registry.createId("google.batchExecute", tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
       spectator_registry.counter(spectator_registry.createId("google.batchSize", tags).withTags(tagDetails)).increment(batchSize)
     }
  }

  public static <T> T timeExecute(Registry spectator_registry, AbstractGoogleClientRequest<T> request, String metric_name, String api, String... tags) throws IOException {
     def success = "false"
     T result
     Clock clock = spectator_registry.clock()
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
       spectator_registry.timer(spectator_registry.createId(metric_name, tags).withTags(tagDetails)).record(nanos, TimeUnit.NANOSECONDS)
     }
     return result
  }
}
