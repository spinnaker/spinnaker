/*
 * Copyright 2022 JPMorgan Chase & Co
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

package com.netflix.spinnaker.orca.clouddriver;

import com.netflix.spinnaker.kork.web.selector.SelectableService;
import java.util.Map;
import retrofit.client.Response;

/**
 * Wrapper around {@link CloudDriverCacheService} which selects an endpoint based on {@link
 * SelectableService.Criteria}. This can be configured to send requests to a specific Clouddriver
 * endpoint based upon predfined crtieria, for example cloud provider or account. Defaults to the
 * default Clouddriver URL if no crtieria are configured.
 */
public class DelegatingClouddriverCacheService
    extends DelegatingClouddriverService<CloudDriverCacheService>
    implements CloudDriverCacheService {

  public DelegatingClouddriverCacheService(SelectableService selectableService) {
    super(selectableService);
  }

  @Override
  public Response forceCacheUpdate(String cloudProvider, String type, Map<String, ?> data) {
    return getService().forceCacheUpdate(cloudProvider, type, data);
  }

  @Override
  public Map<String, Object> clearNamespace(String namespace) {
    return getService().clearNamespace(namespace);
  }
}
