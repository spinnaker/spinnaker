/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.clouddriver.alicloud.provider.view;

import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.clouddriver.alicloud.AliCloudProvider;
import spock.lang.Subject;

public class CommonProvider {

  static final String ACCOUNT = "test-account";
  static final String REGION = "cn-test";

  @Subject ObjectMapper objectMapper = new ObjectMapper();

  @Subject AliCloudProvider oldProvider = new AliCloudProvider();

  final Cache cacheView = mock(Cache.class);
}
