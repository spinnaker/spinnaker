/*
 * Copyright 2026 Harness, Inc. or its affiliates.
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


package com.netflix.spinnaker.clouddriver.lambda.provider.agent;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.clouddriver.lambda.cache.Keys;
import com.netflix.spinnaker.clouddriver.lambda.cache.model.LambdaApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LambdaApplicationProvider implements ApplicationProvider {

  private final Cache cacheView;

  @Override
  public Set<? extends Application> getApplications(boolean expand) {
    String glob = Keys.ID + ":" + Keys.Namespace.LAMBDA_APPLICATIONS + ":*";
    Collection<String> identifiers =
        cacheView.filterIdentifiers(Keys.Namespace.LAMBDA_APPLICATIONS.ns, glob);
    return cacheView.getAll(Keys.Namespace.LAMBDA_APPLICATIONS.ns, identifiers).parallelStream()
        .map(LambdaApplicationProvider::mapCacheDataToLambdaApplication)
        .collect(Collectors.toSet());
  }

  @Override
  public @Nullable Application getApplication(String name) {
    CacheData cacheData =
        cacheView.get(Keys.Namespace.LAMBDA_APPLICATIONS.ns, Keys.getApplicationKey(name));
    if (cacheData == null) {
      return null;
    }
    return mapCacheDataToLambdaApplication(cacheData);
  }

  public static LambdaApplication mapCacheDataToLambdaApplication(CacheData cacheData) {
    LambdaApplication app = new LambdaApplication();
    app.setName(Keys.parse(cacheData.getId()).get("application"));
    app.setAttributes(
        cacheData.getAttributes().entrySet().parallelStream()
            .collect(
                Collectors.toMap(Map.Entry::getKey, entry -> String.valueOf(entry.getValue()))));
    return app;
  }
}
