/*
 * Copyright 2022 Salesforce.com, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.netflix.spinnaker.fiat.config.ResourceProviderConfig;
import com.netflix.spinnaker.fiat.providers.ProviderHealthTracker;
import org.junit.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
public class ClouddriverApplicationLoaderTest {

  @Test
  public void testClouddriverGetApplicationsApiCallInvocation() {

    ProviderHealthTracker providerHealthTracker = mock(ProviderHealthTracker.class);
    ClouddriverApi clouddriverApi = mock(ClouddriverApi.class);
    ResourceProviderConfig.ApplicationProviderConfig applicationProviderConfig =
        new ResourceProviderConfig.ApplicationProviderConfig();
    ClouddriverApplicationLoader applicationLoader =
        new ClouddriverApplicationLoader(
            providerHealthTracker, clouddriverApi, applicationProviderConfig);

    applicationLoader.refreshCache();

    verify(clouddriverApi, times(1)).getApplications();
  }
}
