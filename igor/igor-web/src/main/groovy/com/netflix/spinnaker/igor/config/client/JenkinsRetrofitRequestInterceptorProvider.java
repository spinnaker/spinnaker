/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.igor.config.client;

import com.netflix.spinnaker.igor.config.JenkinsProperties;
import okhttp3.Interceptor;

/** Abstracts away the logic for providing a Retrofit RequestInterceptor for Jenkins services. */
public interface JenkinsRetrofitRequestInterceptorProvider {

  Interceptor provide(JenkinsProperties.JenkinsHost host);
}
