/*
 * Copyright 2018 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cloudfoundry.client;

import static org.mockito.Mockito.mock;

public class MockCloudFoundryClient implements CloudFoundryClient {

  final Spaces spaces = mock(Spaces.class);
  final Organizations organizations = mock(Organizations.class);
  final Domains domains = mock(Domains.class);
  final Routes routes = mock(Routes.class);
  final Applications applications = mock(Applications.class);
  final ServiceInstances serviceInstances = mock(ServiceInstances.class);
  final ServiceKeys serviceKeys = mock(ServiceKeys.class);

  public Spaces getSpaces() {
    return spaces;
  }

  public Organizations getOrganizations() {
    return organizations;
  }

  public Domains getDomains() {
    return domains;
  }

  public Routes getRoutes() {
    return routes;
  }

  public Applications getApplications() {
    return applications;
  }

  public ServiceInstances getServiceInstances() {
    return serviceInstances;
  }

  public ServiceKeys getServiceKeys() {
    return serviceKeys;
  }
}
