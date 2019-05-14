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
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ServiceCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.cache.model.Service;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import com.netflix.spinnaker.clouddriver.model.ApplicationProvider;
import com.netflix.spinnaker.clouddriver.security.AccountCredentials;
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsProvider;
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
  private final AccountCredentialsProvider accountCredentialsProvider;

  @Autowired
  public EcsApplicationProvider(
      AccountCredentialsProvider accountCredentialsProvider,
      ServiceCacheClient serviceCacheClient) {
    this.accountCredentialsProvider = accountCredentialsProvider;
    this.serviceCacheClient = serviceCacheClient;
  }

  @Override
  public Application getApplication(String name) {

    for (Application application : getApplications(true)) {
      if (name.equals(application.getName())) {
        return application;
      }
    }

    return null;
  }

  @Override
  public Set<Application> getApplications(boolean expand) {
    Set<Application> applications = new HashSet<>();

    for (AccountCredentials credentials : accountCredentialsProvider.getAll()) {
      if (credentials instanceof AmazonCredentials) {
        Set<Application> retrievedApplications =
            findApplicationsForAllRegions((AmazonCredentials) credentials, expand);
        applications.addAll(retrievedApplications);
      }
    }

    return applications;
  }

  private Set<Application> findApplicationsForAllRegions(
      AmazonCredentials credentials, boolean expand) {
    Set<Application> applications = new HashSet<>();

    for (AmazonCredentials.AWSRegion awsRegion : credentials.getRegions()) {
      applications.addAll(
          findApplicationsForRegion(credentials.getName(), awsRegion.getName(), expand));
    }

    return applications;
  }

  private Set<Application> findApplicationsForRegion(
      String account, String region, boolean expand) {
    HashMap<String, Application> applicationHashMap =
        populateApplicationMap(account, region, expand);
    return transposeApplicationMapToSet(applicationHashMap);
  }

  private HashMap<String, Application> populateApplicationMap(
      String account, String region, boolean expand) {
    HashMap<String, Application> applicationHashMap = new HashMap<>();
    Collection<Service> services = serviceCacheClient.getAll(account, region);

    for (Service service : services) {
      applicationHashMap = inferApplicationFromServices(applicationHashMap, service, expand);
    }
    return applicationHashMap;
  }

  private Set<Application> transposeApplicationMapToSet(
      HashMap<String, Application> applicationHashMap) {
    Set<Application> applications = new HashSet<>();

    for (Map.Entry<String, Application> entry : applicationHashMap.entrySet()) {
      applications.add(entry.getValue());
    }

    return applications;
  }

  private HashMap<String, Application> inferApplicationFromServices(
      HashMap<String, Application> applicationHashMap, Service service, boolean expand) {

    HashMap<String, String> attributes =
        new HashMap<>(); // After POC we'll figure exactly what info we want to put in here
    String appName = service.getApplicationName();
    String serviceName = service.getServiceName();
    attributes.put("iamRole", service.getRoleArn());
    attributes.put("taskDefinition", service.getTaskDefinition());
    attributes.put("desiredCount", String.valueOf(service.getDesiredCount()));

    HashMap<String, Set<String>> clusterNames = new HashMap<>();
    if (expand) {
      clusterNames.put(appName, Sets.newHashSet(serviceName));
    }

    EcsApplication application = new EcsApplication(appName, attributes, clusterNames);

    if (!applicationHashMap.containsKey(appName)) {
      applicationHashMap.put(appName, application);
    } else {
      applicationHashMap.get(appName).getAttributes().putAll(application.getAttributes());
      if (expand) {
        applicationHashMap.get(appName).getClusterNames().get(appName).add(serviceName);
      }
    }

    return applicationHashMap;
  }
}
