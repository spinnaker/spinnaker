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
import com.netflix.spinnaker.clouddriver.aws.security.AmazonCredentials;
import com.netflix.spinnaker.clouddriver.ecs.cache.Keys;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.ecs.security.NetflixECSCredentials;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.credentials.CredentialsRepository;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
    String glob = Keys.getServiceKey("*", "*", name + "*");
    Collection<String> ecsServices = serviceCacheClient.filterIdentifiers(glob);
    for (Application application : populateApplicationSet(ecsServices, true)) {
      if (name.equals(application.getName())) {
        return application;
      }
    }
    return null;
  }

  @Override
  public Set<EcsApplication> getApplications(boolean expand) {
    Set<EcsApplication> applications = new HashSet<>();
    for (NetflixECSCredentials credentials : credentialsRepository.getAll()) {
      Set<EcsApplication> retrievedApplications =
          findApplicationsForAllRegions(credentials, expand);
      applications.addAll(retrievedApplications);
    }

    return applications;
  }

  private Set<EcsApplication> findApplicationsForAllRegions(
      AmazonCredentials credentials, boolean expand) {
    Set<EcsApplication> applications = new HashSet<>();

    for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
      applications.addAll(
          findApplicationsForRegion(credentials.getName(), awsRegion.getName(), expand));
    }

    return applications;
  }

  private Set<EcsApplication> findApplicationsForRegion(
      String account, String region, boolean expand) {
    HashMap<String, EcsApplication> applicationHashMap =
        populateApplicationMap(account, region, expand);
    return transposeApplicationMapToSet(applicationHashMap);
  }

  private HashMap<String, EcsApplication> populateApplicationMap(
      String account, String region, boolean expand) {
    HashMap<String, EcsApplication> applicationHashMap = new HashMap<>();
    Collection<Service> services = serviceCacheClient.getAll(account, region);

    for (Service service : services) {
      applicationHashMap = inferApplicationFromServices(applicationHashMap, service, expand);
    }
    return applicationHashMap;
  }

  private Set<EcsApplication> populateApplicationSet(
      Collection<String> identifiers, boolean expand) {
    HashMap<String, EcsApplication> applicationHashMap = new HashMap<>();
    Collection<Service> services = serviceCacheClient.getAll(identifiers);

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
    String serviceName = service.getServiceName();
    String accountName = service.getAccount();
    attributes.put("name", appName);

    HashMap<String, Set<String>> clusterNames = new HashMap<>();
    HashMap<String, Set<String>> clusterNamesMetadata = new HashMap<>();

    if (expand) {
      clusterNames.put(accountName, Sets.newHashSet(serviceName));
      clusterNamesMetadata.put(accountName, Sets.newHashSet(moniker.getCluster()));
    }

    EcsApplication application =
        new EcsApplication(appName, attributes, clusterNames, clusterNamesMetadata);

    if (!applicationHashMap.containsKey(appName)) {
      applicationHashMap.put(appName, application);
    } else {
      applicationHashMap.get(appName).getAttributes().putAll(application.getAttributes());
      if (expand) {
        applicationHashMap
            .get(appName)
            .getClusterNames()
            .computeIfAbsent(accountName, k -> Sets.newHashSet())
            .add(serviceName);
        applicationHashMap
            .get(appName)
            .getClusterNameMetadata()
            .computeIfAbsent(accountName, k -> Sets.newHashSet())
            .add(moniker.getCluster());
      }
    }

    return applicationHashMap;
  }
}
