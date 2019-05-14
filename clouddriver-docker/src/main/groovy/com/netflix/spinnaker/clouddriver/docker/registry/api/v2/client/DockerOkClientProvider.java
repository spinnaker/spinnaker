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
package com.netflix.spinnaker.clouddriver.docker.registry.api.v2.client;

import retrofit.client.OkClient;

/** Allows custom configuration of the Docker Registry OkHttpClient. */
public interface DockerOkClientProvider {

  /**
   * @param address Provided simply in case a client provider needs to conditionally apply rules
   *     per-registry
   * @param timeoutMs The client timeout in milliseconds
   * @param insecure Whether or not the registry should be configured to trust all SSL certificates.
   *     If this is true, you may want to fallback to {@code DefaultDockerOkClientProvider}
   * @return An OkClient
   */
  OkClient provide(String address, long timeoutMs, boolean insecure);
}
