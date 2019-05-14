/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.scattergather

import okhttp3.Response

/**
 * Reduces a list of [Response]s to a single response representation.
 *
 * TODO(rz): Refactor to not expose OkHttp3. When we add support for local-scattering, we'll want all existing reducers
 *           to automatically support the new execution path. Would also be handy because we could later add support
 *           for other backends like redis, pubsub, kafka, etc.
 */
interface ResponseReducer {

  fun reduce(responses: List<Response>): ReducedResponse
}
