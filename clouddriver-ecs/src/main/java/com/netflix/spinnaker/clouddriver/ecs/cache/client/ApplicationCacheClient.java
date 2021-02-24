/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under
 * the License.
 */

package com.netflix.spinnaker.clouddriver.ecs.cache.client;

import static com.netflix.spinnaker.clouddriver.ecs.EcsCloudProvider.ID;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static java.util.stream.Collectors.toSet;

import com.netflix.spinnaker.cats.cache.Cache;
import com.netflix.spinnaker.cats.cache.CacheData;
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ApplicationCacheClient extends AbstractCacheClient<Application> {

  @Autowired
  public ApplicationCacheClient(Cache cacheView) {
    super(cacheView, APPLICATIONS.toString());
  }

  @Override
  protected Application convert(CacheData cacheData) {
    log.debug("Translating CacheData to EcsApplication");
    if (cacheData == null) {
      return null;
    }

    String appName = (String) cacheData.getAttributes().get("name");

    Map<String, String> attributes = new HashMap<>();
    attributes.put("name", appName);

    Map<String, Set<String>> clusterNames = new HashMap<>();

    EcsApplication application = new EcsApplication(appName, attributes, clusterNames);

    Set<String> services = getServiceRelationships(cacheData);
    log.info("Found {} services for app {}", services.size(), appName);
    services.forEach(
        key -> {
          Map<String, String> parsedKey = Keys.parse(key);
          if (application.getClusterNames().get(parsedKey.get("account")) != null) {
            application
                .getClusterNames()
                .get(parsedKey.get("account"))
                .add(parsedKey.get("serviceName"));
          } else {
            application
                .getClusterNames()
                .put(
                    parsedKey.get("account"),
                    new HashSet<>(Arrays.asList(parsedKey.get("serviceName"))));
          }
        });

    log.info(
        "Found {} clusterNames for application {}",
        application.getClusterNames(),
        application.getName());
    return application;
  }

  public Application getApplication(String name) {
    return convert(
        cacheView.get(
            APPLICATIONS.ns,
            Keys.getApplicationKey(name),
            RelationshipCacheFilter.include(SERVICES.ns)));
  }

  public Set<Application> getApplications(boolean expand) {
    RelationshipCacheFilter relationshipFilter =
        expand ? RelationshipCacheFilter.include(SERVICES.ns) : RelationshipCacheFilter.none();
    Collection<CacheData> applications =
        cacheView.getAll(
            APPLICATIONS.ns,
            cacheView.filterIdentifiers(APPLICATIONS.ns, ID + ";*"),
            relationshipFilter);
    log.info("getApplications found {} Spinnaker applications in the cache.", applications.size());
    return applications.stream().map(this::convert).collect(toSet());
  }

  private Set<String> getServiceRelationships(CacheData cacheData) {
    Collection<String> serviceRelationships = cacheData.getRelationships().get(SERVICES.ns);
    return serviceRelationships == null
        ? Collections.emptySet()
        : new HashSet<>(serviceRelationships);
  }
}
