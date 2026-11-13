/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.view;

import com.google.common.collect.Sets;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EcsApplicationProvider implements ApplicationProvider {

  private final ServiceCacheClient serviceCacheClient;
  private final CredentialsRepository<NetflixECSCredentials> credentialsRepository;

  @Autowired
  public EcsApplicationProvider(
      CredentialsRepository<NetflixECSCredentials> credentialsRepository,
      ServiceCacheClient serviceCacheClient) {
    this.credentialsRepository = credentialsRepository;
    this.serviceCacheClient = serviceCacheClient;
  }

  @Override
  public Application getApplication(String name) {
    Collection<Service> services =
        serviceCacheClient.canFindByApplication()
            ? serviceCacheClient.findByApplication(name)
            : Collections.emptyList();
    if (services.isEmpty()) {
      // Also the path when the services have not been re-cached with the indexed application yet.
      String glob = Keys.getServiceKey("*", "*", name + "*");
      services = serviceCacheClient.getAll(serviceCacheClient.filterIdentifiers(glob));
    }
    for (Application application : populateApplicationSet(services, true)) {
      if (name.equals(application.getName())) {
        return application;
      }
    }
    return null;
  }

  @Override
  public Set<EcsApplication> getApplications(boolean expand) {
    Map<String, Set<String>> regionsByAccount = new HashMap<>();
    for (NetflixECSCredentials credentials : credentialsRepository.getAll()) {
      Set<String> regions =
          regionsByAccount.computeIfAbsent(credentials.getName(), name -> new HashSet<>());
      credentials.getRegions().forEach(region -> regions.add(region.getName()));
    }

    // One key lookup for every account and region, instead of one per account and region.
    List<String> serviceKeys = new ArrayList<>();
    for (String key : serviceCacheClient.filterIdentifiers(Keys.getServiceKey("*", "*", "*"))) {
      Map<String, String> parts = Keys.parse(key);
      if (parts != null
          && regionsByAccount
              .getOrDefault(parts.get("account"), Collections.emptySet())
              .contains(parts.get("region"))) {
        serviceKeys.add(key);
      }
    }

    // Applications are still built separately for each account and region.
    Map<String, HashMap<String, EcsApplication>> applicationsByAccountAndRegion = new HashMap<>();
    for (Service service : serviceCacheClient.getAll(serviceKeys)) {
      HashMap<String, EcsApplication> applicationHashMap =
          applicationsByAccountAndRegion.computeIfAbsent(
              service.getAccount() + Keys.SEPARATOR + service.getRegion(),
              accountAndRegion -> new HashMap<>());
      inferApplicationFromServices(applicationHashMap, service, expand);
    }

    Set<EcsApplication> applications = new HashSet<>();
    for (HashMap<String, EcsApplication> applicationHashMap :
        applicationsByAccountAndRegion.values()) {
      applications.addAll(transposeApplicationMapToSet(applicationHashMap));
    }
    return applications;
  }

  private Set<EcsApplication> populateApplicationSet(Collection<Service> services, boolean expand) {
    HashMap<String, EcsApplication> applicationHashMap = new HashMap<>();

    for (Service service : services) {
      if (credentialsRepository.has(service.getAccount())) {
        applicationHashMap = inferApplicationFromServices(applicationHashMap, service, expand);
      }
    }
    return transposeApplicationMapToSet(applicationHashMap);
  }

  private Set<EcsApplication> transposeApplicationMapToSet(
      HashMap<String, EcsApplication> applicationHashMap) {
    Set<EcsApplication> applications = new HashSet<>();

    for (Map.Entry<String, EcsApplication> entry : applicationHashMap.entrySet()) {
      applications.add(entry.getValue());
    }

    return applications;
  }

  private HashMap<String, EcsApplication> inferApplicationFromServices(
      HashMap<String, EcsApplication> applicationHashMap, Service service, boolean expand) {

    HashMap<String, String> attributes = new HashMap<>();
    Moniker moniker = service.getMoniker();

    String appName = moniker.getApp();
    String accountName = service.getAccount();
    attributes.put("name", appName);

    HashMap<String, Set<String>> clusterNames = new HashMap<>();

    if (expand) {
      clusterNames.put(accountName, Sets.newHashSet(moniker.getCluster()));
    }

    EcsApplication application = new EcsApplication(appName, attributes, clusterNames);

    if (!applicationHashMap.containsKey(appName)) {
      applicationHashMap.put(appName, application);
    } else {
      applicationHashMap.get(appName).getAttributes().putAll(application.getAttributes());
      if (expand) {
        applicationHashMap
            .get(appName)
            .getClusterNames()
            .computeIfAbsent(accountName, k -> Sets.newHashSet())
            .add(moniker.getCluster());
      }
    }

    return applicationHashMap;
  }
}
