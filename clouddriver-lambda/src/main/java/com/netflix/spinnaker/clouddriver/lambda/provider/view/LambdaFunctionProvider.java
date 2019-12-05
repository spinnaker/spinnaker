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

package com.netflix.spinnaker.clouddriver.lambda.provider.view;

import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.lambda.cache.Keys.Namespace.LAMBDA_FUNCTIONS;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.cache.client.LambdaCacheClient;
import com.netflix.spinnaker.clouddriver.model.Function;
import com.netflix.spinnaker.clouddriver.model.FunctionProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LambdaFunctionProvider implements FunctionProvider {
  private LambdaCacheClient awsLambdaCacheClient;
  private final Cache cacheView;

  @Autowired
  public LambdaFunctionProvider(Cache cacheView) {
    this.awsLambdaCacheClient = new LambdaCacheClient(cacheView);
    this.cacheView = cacheView;
  }

  @Override
  public Collection<Function> getAllFunctions() {
    return new ArrayList<>(awsLambdaCacheClient.getAll());
  }

  public Function getFunction(String account, String region, String functionName) {
    String key = Keys.getLambdaFunctionKey(account, region, functionName);
    return awsLambdaCacheClient.get(key);
  }

  public Set<Function> getApplicationFunctions(String applicationName) {

    CacheData application =
        cacheView.get(
            APPLICATIONS.ns,
            com.netflix.spinnaker.clouddriver.aws.data.Keys.getApplicationKey(applicationName));

    Set<Function> appFunctions = new HashSet<>();
    if (null != application && null != application.getRelationships()) {
      Collection<String> functionRel = application.getRelationships().get(LAMBDA_FUNCTIONS.ns);
      if (null != functionRel && !functionRel.isEmpty()) {
        functionRel.forEach(
            functionKey -> {
              Function function = awsLambdaCacheClient.get(functionKey);
              if (null != function) {
                appFunctions.add(function);
              }
            });
      }
    }
    return appFunctions;
  }
}
