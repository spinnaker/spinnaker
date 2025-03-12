/*
 * Copyright 2022 OpsMx, Inc.
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

package com.netflix.spinnaker.clouddriver.cloudrun.provider.view;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.cloudrun.CloudrunCloudProvider;
import com.netflix.spinnaker.clouddriver.cloudrun.cache.Keys;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunApplication;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CloudrunApplicationProvider implements ApplicationProvider {
  @Autowired private Cache cacheView;
  @Autowired private ObjectMapper objectMapper;

  @Override
  public Set<CloudrunApplication> getApplications(boolean expand) {
    RelationshipCacheFilter filter =
        expand
            ? RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.getNs())
            : RelationshipCacheFilter.none();
    return cacheView
        .getAll(
            Keys.Namespace.APPLICATIONS.getNs(),
            cacheView.filterIdentifiers(
                Keys.Namespace.APPLICATIONS.getNs(), CloudrunCloudProvider.ID + ":*"),
            filter)
        .stream()
        .map(this::applicationFromCacheData)
        .collect(Collectors.toSet());
  }

  @Override
  public CloudrunApplication getApplication(String name) {
    CacheData cacheData =
        cacheView.get(
            Keys.Namespace.APPLICATIONS.getNs(),
            Keys.getApplicationKey(name),
            RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.getNs()));

    if (cacheData == null) {
      return null;
    }
    return applicationFromCacheData(cacheData);
  }

  public CloudrunApplication applicationFromCacheData(CacheData cacheData) {
    CloudrunApplication application =
        objectMapper.convertValue(cacheData.getAttributes(), CloudrunApplication.class);

    if (cacheData.getRelationships().get(Keys.Namespace.CLUSTERS.getNs()) != null) {
      cacheData
          .getRelationships()
          .get(Keys.Namespace.CLUSTERS.getNs())
          .forEach(
              clusterKey -> {
                if (application.getClusterNames().get(Keys.parse(clusterKey).get("account"))
                    != null) {
                  application
                      .getClusterNames()
                      .get(Keys.parse(clusterKey).get("account"))
                      .add(Keys.parse(clusterKey).get("name"));
                } else {
                  Set<String> clusterKeySet = new HashSet<>();
                  clusterKeySet.add(Keys.parse(clusterKey).get("name"));
                  application
                      .getClusterNames()
                      .put(Keys.parse(clusterKey).get("account"), clusterKeySet);
                }
              });
    }
    return application;
  }
}
