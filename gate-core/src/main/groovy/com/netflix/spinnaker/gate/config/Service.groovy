/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.gate.config

import groovy.transform.CompileStatic

@CompileStatic
class Service {
  boolean enabled = true
  String vipAddress
  String baseUrl
  MultiBaseUrl shards
  int priority = 1
  Map<String, Object> config = [:]

  void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl
  }

  static class BaseUrl {
    String vipAddress
    String baseUrl
    int priority = 1
    Map<String, Object> config = [:]
  }

  static class MultiBaseUrl {
    String baseUrl
    List<BaseUrl> baseUrls
  }

  List<BaseUrl> getBaseUrls() {
    if (shards?.baseUrl) {
      return [
        new BaseUrl(baseUrl: shards.baseUrl)
      ]
    } else if (shards?.baseUrls) {
      return shards.baseUrls
    }

    return [new BaseUrl(baseUrl: baseUrl)]
  }
}
