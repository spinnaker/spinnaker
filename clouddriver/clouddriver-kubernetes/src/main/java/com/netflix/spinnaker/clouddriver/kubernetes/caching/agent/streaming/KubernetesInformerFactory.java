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

import com.google.gson.reflect.TypeToken;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.informer.ListerWatcher;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.cache.Caches;
import io.kubernetes.client.informer.cache.DeltaFIFO;
import io.kubernetes.client.informer.cache.Indexer;
import io.kubernetes.client.informer.impl.DefaultSharedIndexInformer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.util.CallGeneratorParams;
import io.kubernetes.client.util.Watchable;
import io.kubernetes.client.util.generic.GenericKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesListObject;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.ListOptions;
import java.lang.reflect.Type;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

public class KubernetesInformerFactory extends SharedInformerFactory {

  private final ApiClient apiClient;

  public KubernetesInformerFactory(ApiClient apiClient, ExecutorService threadPool) {
    super(apiClient, threadPool, false);
    this.apiClient = apiClient;
  }

  public synchronized <ApiType extends KubernetesObject>
      SharedIndexInformer<DynamicKubernetesObject> sharedIndexInformerFor(
          GenericKubernetesApi<DynamicKubernetesObject, DynamicKubernetesListObject>
              genericKubernetesApi,
          Class<ApiType> apiTypeClass,
          Indexer<DynamicKubernetesObject> indexer,
          BiConsumer<Class<DynamicKubernetesObject>, Throwable> exceptionHandler) {
    Type apiType = TypeToken.get(apiTypeClass).getType();
    ListerWatcher<DynamicKubernetesObject, DynamicKubernetesListObject> listerWatcher =
        listerWatcherFor(genericKubernetesApi);
    DeltaFIFO deltaFIFO = new DeltaFIFO(Caches::deletionHandlingMetaNamespaceKeyFunc, indexer);

    SharedIndexInformer<DynamicKubernetesObject> informer =
        new DefaultSharedIndexInformer<>(
            DynamicKubernetesObject.class, listerWatcher, 0L, deltaFIFO, indexer, exceptionHandler);

    this.informers.putIfAbsent(apiType, informer);
    return informer;
  }

  private ListerWatcher<DynamicKubernetesObject, DynamicKubernetesListObject> listerWatcherFor(
      GenericKubernetesApi<DynamicKubernetesObject, DynamicKubernetesListObject>
          genericKubernetesApi) {
    if (apiClient.getReadTimeout() > 0) {
      // set read timeout zero to ensure client doesn't time out
      apiClient.setReadTimeout(0);
    }
    return new ListerWatcher<>() {
      public DynamicKubernetesListObject list(CallGeneratorParams params) throws ApiException {
        return genericKubernetesApi.list(getListOptions(params)).throwsApiException().getObject();
      }

      public Watchable<DynamicKubernetesObject> watch(CallGeneratorParams params)
          throws ApiException {
        return genericKubernetesApi.watch(getListOptions(params));
      }

      private ListOptions getListOptions(CallGeneratorParams params) {
        return new ListOptions() {
          {
            setResourceVersion(params.resourceVersion);
            setTimeoutSeconds(params.timeoutSeconds);
          }
        };
      }
    };
  }
}
