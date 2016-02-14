/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.discovery

import retrofit.RestAdapter
import retrofit.converter.Converter

import java.util.regex.Pattern

class DiscoveryApiFactory {

  private Converter discoveryConverter

  DiscoveryApiFactory(Converter discoveryConverter) {
    this.discoveryConverter = discoveryConverter
  }

  public DiscoveryApi createApi(String endpointTemplate, String region) {
    new RestAdapter.Builder()
      .setConverter(discoveryConverter)
      .setEndpoint(endpointTemplate.replaceAll(Pattern.quote('{{region}}'), region))
      .build()
      .create(DiscoveryApi)
  }
}
