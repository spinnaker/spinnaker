/*
 * Copyright 2025 Wise, PLC.
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

package com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.ListOptions;

/**
 * Adapter class to hide {@link io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi}
 * complexity from {@link
 * com.netflix.spinnaker.clouddriver.kubernetes.caching.agent.streaming.KubernetesStreamingWatcher}
 * and to simplify testing. To an extent similar to {@see
 * io.kubernetes.client.informer.ListerWatcher} in original Informer implementation.
 */
public class K8SListWatchAdapter {
  public static final String RESOURCE_VERSION_USE_FROM_CACHE = "0";
  public static final String RESOURCE_VERSION_LIST_ALL = "";
  private final DynamicKubernetesApi api;

  public K8SListWatchAdapter(
      String group, String version, String resourcePlural, ApiClient apiClient) {
    api = new DynamicKubernetesApi(group, version, resourcePlural, apiClient);
  }

  DynamicKubernetesListObject list(String lastSyncResourceVersion, int limit, String continueFrom)
      throws ApiException {
    ListOptions listOptions = getListOptions(null, lastSyncResourceVersion);
    if (limit > 0) {
      listOptions.setLimit(limit);
      if (continueFrom != null) {
        listOptions.setContinue(continueFrom);
      }
    }
    return api.list(listOptions).throwsApiException().getObject();
  }

  private ListOptions getListOptions(Integer timeoutSeconds, String lastSyncResourceVersion) {
    return new ListOptions() {
      {
        setTimeoutSeconds(timeoutSeconds);
        setResourceVersion(lastSyncResourceVersion);
      }
    };
  }

  public Watchable<DynamicKubernetesObject> watch(
      Integer timeoutSeconds, String lastSyncResourceVersion) throws ApiException {
    return api.watch(getListOptions(timeoutSeconds, lastSyncResourceVersion));
  }
}
