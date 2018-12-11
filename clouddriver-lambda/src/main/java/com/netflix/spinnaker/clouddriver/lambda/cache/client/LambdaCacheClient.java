/*
 * Copyright 2018 Amazon.com, Inc. or its affiliates.
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

package com.netflix.spinnaker.clouddriver.lambda.cache.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.awsobjectmapper.AmazonObjectMapperConfigurer;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaFunction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;

@Component
public class LambdaCacheClient extends AbstractCacheClient<LambdaFunction> {
  private final ObjectMapper objectMapper = AmazonObjectMapperConfigurer.createConfigured();

  @Autowired
  public LambdaCacheClient(Cache cacheView) {
    super(cacheView, LAMBDA_FUNCTIONS.ns);
  }

  @Override
  protected LambdaFunction convert(CacheData cacheData) {
    Map<String, Object> attributes = cacheData.getAttributes();
    return objectMapper.convertValue(attributes, LambdaFunction.class);
  }
}
