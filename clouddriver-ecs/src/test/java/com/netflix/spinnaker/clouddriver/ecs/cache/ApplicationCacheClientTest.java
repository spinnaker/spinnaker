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

package com.netflix.spinnaker.clouddriver.ecs.cache;

import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.APPLICATIONS;
import static com.netflix.spinnaker.clouddriver.ecs.cache.Keys.Namespace.SERVICES;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.when;

import com.google.common.collect.Sets;
import com.netflix.spinnaker.cats.cache.DefaultCacheData;
import com.netflix.spinnaker.clouddriver.ecs.cache.client.ApplicationCacheClient;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsApplication;
import com.netflix.spinnaker.clouddriver.model.Application;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;
import spock.lang.Subject;

public class ApplicationCacheClientTest extends CommonCacheClient {

  @Subject private final ApplicationCacheClient client = new ApplicationCacheClient(cacheView);

  @Test
  public void shouldConvert() {
    // Given
    String key = Keys.getApplicationKey(APP_NAME);

    Map<String, String> appAttributes = new HashMap<>();
    appAttributes.put("name", APP_NAME);

    Map<String, Set<String>> clusterNames = new HashMap<>();

    Application application = new EcsApplication(APP_NAME, appAttributes, clusterNames);

    Map<String, Object> cacheAttributes = new HashMap<>();
    cacheAttributes.put("name", application.getName());

    when(cacheView.get(APPLICATIONS.toString(), key))
        .thenReturn(new DefaultCacheData(key, cacheAttributes, Collections.emptyMap()));

    // When
    Application retrievedApplication = client.get(key);

    // Then
    assertTrue(
        "Expected the application to be " + application + " but got " + retrievedApplication,
        application.equals(retrievedApplication));
  }

  @Test
  public void shouldGetApplication() {
    // Given
    String key = Keys.getApplicationKey(APP_NAME);
    String serviceKey = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME);

    Map<String, String> appAttributes = new HashMap<>();
    appAttributes.put("name", APP_NAME);

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(SERVICES.ns, Sets.newHashSet(serviceKey));

    Map<String, Set<String>> clusterNames = new HashMap<>();
    clusterNames.put(ACCOUNT, Sets.newHashSet(SERVICE_NAME));

    Application application = new EcsApplication(APP_NAME, appAttributes, clusterNames);

    Map<String, Object> cacheAttributes = new HashMap<>();
    cacheAttributes.put("name", application.getName());

    when(cacheView.get(eq(APPLICATIONS.ns), eq(key), any()))
        .thenReturn(new DefaultCacheData(key, cacheAttributes, relationships));

    // When
    Application retrievedApplication = client.getApplication(APP_NAME);

    // Then
    assertTrue(
        "Expected the application to be " + application + " but got " + retrievedApplication,
        application.equals(retrievedApplication));
  }

  @Test
  public void shouldGetApplications() {
    // Given
    String key = Keys.getApplicationKey(APP_NAME);
    String serviceKey = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME);

    Map<String, String> appAttributes = new HashMap<>();
    appAttributes.put("name", APP_NAME);

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(SERVICES.ns, Sets.newHashSet(serviceKey));

    Map<String, Set<String>> clusterNames = new HashMap<>();
    clusterNames.put(ACCOUNT, Sets.newHashSet(SERVICE_NAME));

    Application application = new EcsApplication(APP_NAME, appAttributes, clusterNames);

    Map<String, Object> cacheAttributes = new HashMap<>();
    cacheAttributes.put("name", application.getName());

    when(cacheView.getAll(eq(APPLICATIONS.ns), any(), any()))
        .thenReturn(Sets.newHashSet(new DefaultCacheData(key, cacheAttributes, relationships)));

    // When
    Set<Application> retrievedApplication = client.getApplications(false);

    // Then
    assertTrue(
        "Expected the application to be " + application + " but got " + retrievedApplication,
        Sets.newHashSet(application).equals(retrievedApplication));
  }

  @Test
  public void shouldGetApplicationsExpanded() {
    // Given
    String key = Keys.getApplicationKey(APP_NAME);
    String serviceKey = Keys.getServiceKey(ACCOUNT, REGION, SERVICE_NAME);

    Map<String, String> appAttributes = new HashMap<>();
    appAttributes.put("name", APP_NAME);

    Map<String, Collection<String>> relationships = new HashMap<>();
    relationships.put(SERVICES.ns, Sets.newHashSet(serviceKey));

    Map<String, Set<String>> clusterNames = new HashMap<>();
    clusterNames.put(ACCOUNT, Sets.newHashSet(SERVICE_NAME));

    Application application = new EcsApplication(APP_NAME, appAttributes, clusterNames);

    Map<String, Object> cacheAttributes = new HashMap<>();
    cacheAttributes.put("name", application.getName());

    when(cacheView.getAll(eq(APPLICATIONS.ns), any(), any()))
        .thenReturn(Sets.newHashSet(new DefaultCacheData(key, cacheAttributes, relationships)));

    // When
    Set<Application> retrievedApplication = client.getApplications(true);

    // Then
    assertTrue(
        "Expected the application to be " + application + " but got " + retrievedApplication,
        Sets.newHashSet(application).equals(retrievedApplication));
  }
}
